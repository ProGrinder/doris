// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import org.codehaus.groovy.runtime.IOGroovyMethods

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

suite("test_map_load_and_compaction", "p0") {
    // define a sql table
    def testTable = "tbl_test_map_compaction"
    def dataFile = "map_2_rows.json";
    def dataFile1 = "map_4093_rows.json"

    sql "DROP TABLE IF EXISTS ${testTable}"
    sql "ADMIN SET FRONTEND CONFIG ('enable_map_type' = 'true')"

    sql """
        CREATE TABLE IF NOT EXISTS ${testTable} (
            id BIGINT,
            actor Map<STRING, STRING>
        )
        DUPLICATE KEY(id)
        DISTRIBUTED BY HASH(id) BUCKETS 10
        PROPERTIES("replication_num" = "1", "disable_auto_compaction" = "true");
        """
    def streamLoadJson = {assertLoadNum, fileName->
        // load the json data
        streamLoad {
            table testTable

            // set http request header params
            set 'read_json_by_line', 'true'
            set 'format', 'json'
            file fileName // import json file
            time 10000 // limit inflight 10s

            // if declared a check callback, the default check condition will ignore.
            // So you must check all condition
            check { result, exception, startTime, endTime ->
                if (exception != null) {
                    throw exception
                }
                log.info("Stream load result: ${result}".toString())
                def json = parseJson(result)
                assertEquals("success", json.Status.toLowerCase())
                assertEquals("OK", json.Message)
                log.info("expect ", assertLoadNum)
                log.info("now: ",  json.NumberTotalRows)
                assertTrue(assertLoadNum==json.NumberTotalRows)
                assertTrue(json.LoadBytes > 0)
            }
        }
    }

    def checkCompactionStatus = {compactionStatus, assertRowSetNum->
        String checkStatus = new String("curl -X GET "+compactionStatus)
        process = checkStatus.execute()
        code = process.waitFor()
        err = IOGroovyMethods.getText(new BufferedReader(new InputStreamReader(process.getErrorStream())));
        out = process.getText()
        logger.info("Check compaction status: code=" + code + ", out=" + out + ", err=" + err)
        assertEquals(code, 0)
        def compactStatusJson = parseJson(out.trim())
        assert compactStatusJson.rowsets instanceof List
        int rowsetsCount = 0
        for (String rowset in (List<String>) compactStatusJson.rowsets) {
            rowsetsCount += Integer.parseInt(rowset.split(" ")[1])
        }
        assertTrue(assertRowSetNum==rowsetsCount)
    }


    // try trigger compaction manually and then check backends is still alive
    try {
        // load the map data from json file
        streamLoadJson.call(2, dataFile)

        for (int i = 0; i < 5; ++i) {
            streamLoadJson.call(4063, dataFile1)
        }

        // check result
        qt_select "SELECT count(*) FROM ${testTable};"

        // check here 2 rowsets
        //TabletId,ReplicaId,BackendId,SchemaHash,Version,LstSuccessVersion,LstFailedVersion,LstFailedTime,LocalDataSize,RemoteDataSize,RowCount,State,LstConsistencyCheckTime,CheckVersion,VersionCount,PathHash,MetaUrl,CompactionStatus
        String[][] tablets = sql """ show tablets from ${testTable}; """
        String[] tablet = tablets[0]
        // check rowsets number
        String compactionStatus = tablet[17]
        checkCompactionStatus.call(compactionStatus, 6)

        // trigger compaction
        String[][] backends = sql """ show backends; """
        assertTrue(backends.size() > 0)
        String backend_id;
        def backendId_to_backendIP = [:]
        def backendId_to_backendHttpPort = [:]
        for (String[] backend in backends) {
            backendId_to_backendIP.put(backend[0], backend[2])
            backendId_to_backendHttpPort.put(backend[0], backend[5])
        }
        String tablet_id = tablet[0]
        backend_id = tablet[2]
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST http://")
        sb.append(backendId_to_backendIP.get(backend_id))
        sb.append(":")
        sb.append(backendId_to_backendHttpPort.get(backend_id))
        sb.append("/api/compaction/run?tablet_id=")
        sb.append(tablet_id)
        sb.append("&compact_type=cumulative")

        String command = sb.toString()
        process = command.execute()
        code = process.waitFor()
        err = IOGroovyMethods.getText(new BufferedReader(new InputStreamReader(process.getErrorStream())));
        out = process.getText()
        logger.info("Run compaction: code=" + code + ", out=" + out + ", err=" + err)
        assertEquals(code, 0)
        def compactJson = parseJson(out.trim())
        assertEquals("success", compactJson.status.toLowerCase())

        // wait compactions done
        do {
            Thread.sleep(1000)
            sb = new StringBuilder();
            sb.append("curl -X GET http://")
            sb.append(backendId_to_backendIP.get(backend_id))
            sb.append(":")
            sb.append(backendId_to_backendHttpPort.get(backend_id))
            sb.append("/api/compaction/run_status?tablet_id=")
            sb.append(tablet_id)

            String cm = sb.toString()
            logger.info(cm)
            process = cm.execute()
            code = process.waitFor()
            err = IOGroovyMethods.getText(new BufferedReader(new InputStreamReader(process.getErrorStream())));
            out = process.getText()
            logger.info("Get compaction status: code=" + code + ", out=" + out + ", err=" + err)
            assertEquals(code, 0)
            def cs = parseJson(out.trim())
            assertEquals("success", cs.status.toLowerCase())
            running = cs.run_status
        } while (running)

        checkCompactionStatus.call(compactionStatus, 1)

        // finally check backend alive
        backends = sql """ show backends; """
        assertTrue(backends.size() > 0)
        for (String[] b : backends) {
            assertEquals("true", b[9])
        }
    } finally {
        try_sql("DROP TABLE IF EXISTS ${testTable}")
    }
}
