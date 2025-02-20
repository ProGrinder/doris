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
#pragma once

#include <common/status.h>
#include <vec/columns/column.h>
#include <vec/json/json_parser.h>
#include <vec/json/simd_json_parser.h>

namespace doris::vectorized {

// parse a batch of json strings into column object
Status parse_json_to_variant(IColumn& column, const std::vector<StringRef>& jsons);

// parse a single json
Status parse_json_to_variant(IColumn& column, const StringRef& jsons,
                             JSONDataParser<SimdJSONParser>* parser);

// extract keys columns from json strings into columns
bool extract_key(MutableColumns& columns, const std::vector<StringRef>& jsons,
                 const std::vector<StringRef>& keys, const std::vector<ExtractType>& types);

// extract keys columns from colunnstring(json format) into columns
bool extract_key(MutableColumns& columns, const ColumnString& json_column,
                 const std::vector<StringRef>& keys, const std::vector<ExtractType>& types);
} // namespace doris::vectorized
