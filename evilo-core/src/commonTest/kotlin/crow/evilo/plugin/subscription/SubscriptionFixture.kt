package crow.evilo.plugin.subscription

/**
 * One index as `evilo-subscribe-template/scripts/build_index.py` produced it, embedded so
 * the checks run without a network or a local file. Regenerate it when the index shape
 * changes; the signature covers these exact bytes.
 */
internal object SubscriptionFixture {
    const val SIGNATURE_HEX = "da1bb332da6eaf535226c62734f1ad3ecb678bd690dbccc7603eae3c679605d5398889b4149568351d657d33e46861f76f6e33b6c20d127bb286ac8c92402401"

    const val PUBLIC_KEY_HEX = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"

    const val FINGERPRINT = "21fe31dfa154a261626bf854046fd2271b7bed4b6abe45aa58877ef47f9721b9"

    const val INDEX_BODY = """{"name":"Awesome evilo plugins","plugins":[{"catalogUrl":"https://raw.githubusercontent.com/crowforkotlin/evilo-plugin-copymanga/main/catalog.json","plugin":{"authors":["evilo contributors"],"availableVersions":[{"manifestSha256":"fde0c782bb879adab9301254aefb4e283d5631aec9f1b8e05679c73f4158d7e8","manifestUrl":"https://raw.githubusercontent.com/crowforkotlin/evilo-plugin-copymanga/package-v1.0.0/releases/1.0.0/manifest.wlm","publishedAt":"2026-11-19T00:00:00Z","signing":{"algorithm":"Ed25519","keyId":"evilo-root-v1","publicKeyHex":"cc42792de95ab03ba25ca9dda958b1951a976f5cedfd59e4f1d3190a08487bb1"},"sourceArchiveSha256":"77c060458c20bb4473650a70b715fbc397cea52e1b9e38f9b1305ebd9e650732","sourceArchiveUrl":"https://raw.githubusercontent.com/crowforkotlin/evilo-plugin-copymanga/package-v1.0.0/releases/1.0.0/crow.evilo.source.copymanga-1.0.0-sources.zip","sourceRevision":"9e982cc494819f3640d49b0cd35e91eb9a75464e","sourceUrl":"https://github.com/crowforkotlin/evilo-plugin-copymanga/tree/9e982cc494819f3640d49b0cd35e91eb9a75464e","version":"1.0.0","versionCode":1}],"createdAt":"2026-11-09","description":"CopyManga search and catalog with metadata, mirror-served details, chapter groups and ordered image pages. Short pages advance by returned counts; available mirrors are retried for HTTP and empty-payload failures.","iconUrl":"https://raw.githubusercontent.com/crowforkotlin/evilo-plugin-copymanga/package-v1.0.0/releases/1.0.0/icon.png","id":"crow.evilo.source.copymanga","kinds":["manga"],"languages":["zh"],"name":"CopyManga","nsfw":true,"overseas":true,"sourceUrl":"https://github.com/crowforkotlin/evilo-plugin-copymanga"}}],"schemaVersion":1,"updatedAt":"2026-11-19T00:00:00Z"}"""

    fun pointer(indexUrl: String): String = """
        {"name":"Awesome evilo plugins","indexUrl":"$indexUrl",
         "signingKeyFingerprint":"$FINGERPRINT","keyId":"subscription"}
    """.trimIndent()
}
