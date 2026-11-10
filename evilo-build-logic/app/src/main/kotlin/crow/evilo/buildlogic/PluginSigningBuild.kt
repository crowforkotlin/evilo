package crow.evilo.buildlogic

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.EdECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

const val EVILO_PLUGIN_KEY_ID = "evilo-root-v1"

fun Project.eviloPluginKeyDirectory(): File = providers.gradleProperty("evilo.signingKeyDirectory")
    .orNull?.let(rootProject::file)
    ?: File(providers.systemProperty("user.home").get(), ".evilo/signing")

@DisableCachingByDefault(because = "Private signing keys must remain on the local machine.")
abstract class GenerateEviloPluginKeys : DefaultTask() {
    @get:OutputDirectory
    abstract val keyDirectory: DirectoryProperty

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = keyDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val repository = repositoryDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(!directory.startsWith(repository)) { "Plugin signing keys must be stored outside the repository." }
        if (Files.exists(directory)) {
            require(!directory.toRealPath().startsWith(repository.toRealPath())) { "Plugin key directory points inside the repository." }
        }
        val privateFile = directory.resolve("private.key")
        val publicFile = directory.resolve("public.key")
        val metadataFile = directory.resolve("authority.properties")
        require(!Files.isSymbolicLink(privateFile) && !Files.isSymbolicLink(publicFile) && !Files.isSymbolicLink(metadataFile)) {
            "Plugin authority files must not be symlinks."
        }
        require(!Files.exists(metadataFile) || (Files.isRegularFile(privateFile) && Files.isRegularFile(publicFile))) {
            "The preserved authority metadata has no complete key pair; restore it from backup."
        }
        if (Files.exists(privateFile) || Files.exists(publicFile)) {
            require(Files.isRegularFile(privateFile) && Files.isRegularFile(publicFile)) {
                "The plugin authority pair is incomplete; restore it from backup. Existing files are never overwritten."
            }
            require(listOf(privateFile, publicFile).all { path ->
                val hex = Files.readString(path).trim()
                hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            }) { "Existing authority keys must be raw 32-byte hexadecimal keys." }
            val seed = Files.readString(privateFile).trim().hexToByteArray()
            val publicKey = Files.readString(publicFile).trim().hexToByteArray()
            val factory = KeyFactory.getInstance("Ed25519")
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec("302e020100300506032b657004220420".hexToByteArray() + seed))
            val key = factory.generatePublic(X509EncodedKeySpec("302a300506032b6570032100".hexToByteArray() + publicKey))
            val probe = "evilo.plugin.authority".toByteArray(Charsets.US_ASCII)
            val signer = Signature.getInstance("Ed25519").apply { initSign(privateKey); update(probe) }
            val verifier = Signature.getInstance("Ed25519").apply { initVerify(key); update(probe) }
            require(verifier.verify(signer.sign())) { "The authority private/public keys do not match. Restore the original pair." }
            writeAuthorityMetadata(metadataFile, publicKey.toHexString())
            return
        }

        Files.createDirectories(directory)
        require(!directory.toRealPath().startsWith(repository.toRealPath())) { "Plugin key directory points inside the repository." }
        val posix = directory.fileSystem.supportedFileAttributeViews().contains("posix")
        if (posix) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val seed = (pair.private as EdECPrivateKey).bytes.orElseThrow()
        val encodedPublicKey = pair.public.encoded
        require(seed.size == 32 && encodedPublicKey.size == 44) { "Unexpected JDK Ed25519 key encoding." }
        val publicKey = encodedPublicKey.copyOfRange(encodedPublicKey.size - 32, encodedPublicKey.size)
        if (posix) Files.createFile(privateFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        else Files.createFile(privateFile)
        Files.writeString(privateFile, seed.toHexString() + "\n")
        Files.createFile(publicFile)
        Files.writeString(publicFile, publicKey.toHexString() + "\n")
        writeAuthorityMetadata(metadataFile, publicKey.toHexString())
        logger.lifecycle("Created the evilo plugin authority. Only its public key is compiled into the application.")
    }

    private fun writeAuthorityMetadata(path: java.nio.file.Path, publicKey: String) {
        require(!Files.isSymbolicLink(path)) { "Authority metadata must not be a symlink." }
        if (Files.exists(path)) {
            val metadata = Properties().apply { Files.newInputStream(path).use(::load) }
            require(metadata.getProperty("keyId") == EVILO_PLUGIN_KEY_ID && metadata.getProperty("publicKeyHex") == publicKey) {
                "Authority metadata does not match the preserved signing pair."
            }
        } else Files.writeString(path, "keyId=$EVILO_PLUGIN_KEY_ID\npublicKeyHex=$publicKey\n", java.nio.file.StandardOpenOption.CREATE_NEW)
    }
}

abstract class GenerateEviloAppConfiguration : DefaultTask() {
    @get:InputFile
    abstract val publicKeyFile: RegularFileProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Long>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val key = publicKeyFile.get().asFile.readText().trim().lowercase()
        require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid Ed25519 authority public key." }
        val version = appVersion.get()
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) { "Invalid application release version." }
        require(appVersionCode.get() > 0) { "Application version code must be positive." }
        val file = outputDirectory.file("crow/evilo/app/GeneratedEviloConfiguration.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package crow.evilo.app

            internal object GeneratedEviloConfiguration {
                const val PLUGIN_KEY_ID = "$EVILO_PLUGIN_KEY_ID"
                const val PLUGIN_PUBLIC_KEY_HEX = "$key"
                const val APP_VERSION = "$version"
                const val APP_VERSION_CODE = ${appVersionCode.get()}L
            }
            """.trimIndent() + "\n",
        )
    }
}
