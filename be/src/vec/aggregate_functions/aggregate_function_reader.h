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

#include "vec/aggregate_functions/aggregate_function_bitmap.h"
#include "vec/aggregate_functions/aggregate_function_hll_union_agg.h"
#include "vec/aggregate_functions/aggregate_function_min_max.h"
#include "vec/aggregate_functions/aggregate_function_quantile_state.h"
#include "vec/aggregate_functions/aggregate_function_reader_first_last.h"
#include "vec/aggregate_functions/aggregate_function_simple_factory.h"
#include "vec/aggregate_functions/aggregate_function_sum.h"

namespace doris::vectorized {

static auto constexpr AGG_READER_SUFFIX = "_reader";
static auto constexpr AGG_LOAD_SUFFIX = "_load";

void register_aggregate_function_reader_load(AggregateFunctionSimpleFactory& factory);

void register_aggregate_function_replace_reader_load(AggregateFunctionSimpleFactory& factory);

} // namespace doris::vectorized
