import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

async function workerHarness(columnNames) {
    const source = await readFile(new URL('../../webMain/resources/sqlite-worker/worker.js', import.meta.url), 'utf8');
    const replies = [];
    let listener;
    let finalized = false;
    const statement = {
        parameterCount: 0,
        columnCount: columnNames.length,
        getColumnNames() {
            assert.notEqual(this.columnCount, 0, 'OO1 rejects column metadata for write-only statements');
            return columnNames;
        },
        reset() {},
        resetNoThrow() {},
        step() { return false; },
        finalize() { finalized = true; },
    };
    class Database {
        prepare() { return statement; }
        close() {}
    }
    const sqlite = { installOpfsSAHPoolVfs: async () => ({ OpfsSAHPoolDb: Database, pauseVfs() {} }), capi: { SQLITE_NULL: 5 } };
    const context = vm.createContext({
        sqlite3InitModule: async () => sqlite,
        navigator: { locks: { request: (_name, _options, callback) => callback({}) } },
        self: { addEventListener: (_event, callback) => { listener = callback; }, postMessage: reply => replies.push(reply) },
        URL, Uint8Array, ArrayBuffer,
    });
    // Only replace module plumbing; execute the actual worker dispatch and queue implementation.
    const executable = source.replace(/^import sqlite3InitModule[^\n]*\n/, '')
        .replaceAll('import.meta.url', JSON.stringify('https://example.invalid/sqlite-worker/worker.js'));
    vm.runInContext(executable, context);
    let id = 1;
    async function request(data) {
        const current = id++;
        listener({ data: { id: current, data } });
        for (let attempt = 0; attempt < 100; attempt++) {
            await new Promise(resolve => setImmediate(resolve));
            const reply = replies.find(item => item.id === current);
            if (reply) {
                assert.equal(reply.error, undefined);
                return JSON.parse(JSON.stringify(reply.data));
            }
        }
        assert.fail('Worker did not reply');
    }
    const { databaseId } = await request({ cmd: 'open', fileName: 'test.db' });
    return { request, databaseId, get finalized() { return finalized; } };
}

test('write-only PRAGMA and DDL prepare with zero result columns', async () => {
    const harness = await workerHarness([]);
    for (const sql of ['PRAGMA foreign_keys = ON', 'CREATE TABLE test(value INTEGER)']) {
        const prepared = await harness.request({ cmd: 'prepare', databaseId: harness.databaseId, sql });
        assert.deepEqual(prepared.columnNames, []);
        const result = await harness.request({ cmd: 'step', statementId: prepared.statementId, bindings: [] });
        assert.deepEqual(result, { rows: [], columnTypes: [] });
    }
});

test('query preparation retains result column names', async () => {
    const harness = await workerHarness(['value']);
    const prepared = await harness.request({ cmd: 'prepare', databaseId: harness.databaseId, sql: 'SELECT 1 AS value' });
    assert.deepEqual(prepared.columnNames, ['value']);
});
