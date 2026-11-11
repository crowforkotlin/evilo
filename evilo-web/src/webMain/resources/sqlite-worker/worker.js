import sqlite3InitModule from "./sqlite3/index.mjs";

let sqlite3;
let pool;
let nextDatabaseId = 1;
let nextStatementId = 1;
let closed = false;
let commands = Promise.resolve();
const databases = new Map();
const statements = new Map();

let resolveReady;
let rejectReady;
const ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
});
ready.catch(() => {});
let releaseOwnership;
const lifetime = new Promise((resolve) => { releaseOwnership = resolve; });

// Keep one owner per origin; another tab must not open a second SAH pool over these files.
const ownership = navigator.locks.request("evilo-sqlite-owner", { mode: "exclusive", ifAvailable: true }, async (lock) => {
    if (!lock) throw new Error("Another evilo tab owns local storage. Close it before retrying.");
    sqlite3 = await sqlite3InitModule({
        locateFile: (path) => new URL(`./sqlite3/${path}`, import.meta.url).href,
    });
    pool = await sqlite3.installOpfsSAHPoolVfs({
        name: "evilo-opfs",
        directory: "/evilo-sqlite",
        initialCapacity: 6,
        clearOnInit: false,
        preserveOnInitFailure: true,
    });
    resolveReady();
    await lifetime;
    pool.pauseVfs();
});
ownership.catch(rejectReady);

function message(error) {
    return error instanceof Error ? error.message : String(error);
}

function required(map, id, label) {
    const value = map.get(id);
    if (!value) throw new Error(`Unknown SQLite ${label}: ${id}.`);
    return value;
}

function sqlValue(value) {
    if (value == null) return null;
    if (typeof value === "bigint") {
        const number = Number(value);
        if (!Number.isSafeInteger(number)) throw new RangeError("SQLite integer exceeds the Web driver's exact numeric range.");
        return number;
    }
    if (typeof value === "number") {
        if (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value))) {
            throw new RangeError("SQLite number cannot be transported exactly by the Web driver.");
        }
        return value;
    }
    if (typeof value === "string") return value;
    if (ArrayBuffer.isView(value)) return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    if (value instanceof ArrayBuffer) return new Uint8Array(value);
    throw new TypeError("Unsupported SQLite value.");
}

function closeStatement(id) {
    const entry = statements.get(id);
    if (entry) {
        try { entry.statement.finalize(); }
        finally { statements.delete(id); }
    }
}

function closeDatabase(id) {
    const entry = databases.get(id);
    if (entry) {
        try {
            for (const [statementId, statement] of statements) {
                if (statement.databaseId === id) closeStatement(statementId);
            }
        } finally {
            try { entry.database.close(); }
            finally { databases.delete(id); }
        }
    }
}

function dispatch(data) {
    if (closed) throw new Error("The SQLite Worker is closed.");
    switch (data.cmd) {
        case "open": {
            if (typeof data.fileName !== "string" || !/^[a-zA-Z0-9_-][a-zA-Z0-9._-]*$/.test(data.fileName)) {
                throw new Error("SQLite requires a persistent database file name without directory components.");
            }
            if (databases.size !== 0) throw new Error("The evilo Web database uses a single connection.");
            const database = new pool.OpfsSAHPoolDb(`/${data.fileName}`, "c");
            const databaseId = nextDatabaseId++;
            databases.set(databaseId, { database });
            return { databaseId };
        }
        case "prepare": {
            const entry = required(databases, data.databaseId, "database");
            const statement = entry.database.prepare(data.sql);
            const statementId = nextStatementId++;
            statements.set(statementId, { statement, databaseId: data.databaseId });
            return { statementId, parameterCount: statement.parameterCount, columnNames: statement.getColumnNames() };
        }
        case "step": {
            const { statement } = required(statements, data.statementId, "statement");
            statement.reset(true);
            try {
                const bindings = data.bindings ?? [];
                if (bindings.length > statement.parameterCount) throw new Error("Too many SQLite statement bindings.");
                for (let index = 0; index < statement.parameterCount; index++) statement.bind(index + 1, sqlValue(bindings[index]));
                const rows = [];
                const columnTypes = new Array(statement.columnCount).fill(sqlite3.capi.SQLITE_NULL);
                while (statement.step()) {
                    if (rows.length === 0) {
                        for (let index = 0; index < statement.columnCount; index++) {
                            columnTypes[index] = sqlite3.capi.sqlite3_column_type(statement.pointer, index);
                        }
                    }
                    rows.push(statement.get([]).map(sqlValue));
                }
                return { rows, columnTypes };
            } finally {
                // Preserve a step failure while releasing statement locks and bound memory.
                statement.resetNoThrow(true);
            }
        }
        case "close":
            if (data.statementId != null) closeStatement(data.statementId);
            if (data.databaseId != null) closeDatabase(data.databaseId);
            return null;
        default:
            throw new Error(`Unsupported SQLite command: ${data.cmd}.`);
    }
}

async function shutdown() {
    closed = true;
    const failures = [];
    for (const id of [...statements.keys()]) {
        try { closeStatement(id); } catch (error) { failures.push(message(error)); }
    }
    for (const id of [...databases.keys()]) {
        try { closeDatabase(id); } catch (error) { failures.push(message(error)); }
    }
    if (failures.length) throw new Error(`SQLite cleanup failed: ${failures.join("; ")}`);
    releaseOwnership();
    await ownership;
}

self.addEventListener("message", ({ data: request }) => {
    if (request?.eviloInitialize === true) {
        ready.then(
            () => self.postMessage({ id: -1, eviloReady: true }),
            (error) => self.postMessage({ id: -1, error: message(error) }),
        );
        return;
    }
    commands = commands.then(async () => {
        const id = request?.id;
        try {
            await ready;
            if (request?.eviloShutdown === true) {
                await shutdown();
                self.postMessage({ id: -1, eviloClosed: true });
            } else {
                const result = dispatch(request.data);
                if (request.data.cmd !== "close") self.postMessage({ id, data: result });
            }
        } catch (error) {
            self.postMessage({ id: id ?? -1, error: message(error) });
        }
    });
});
