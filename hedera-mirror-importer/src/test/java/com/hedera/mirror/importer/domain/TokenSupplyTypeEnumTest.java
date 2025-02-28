package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.junit.jupiter.api.Test;

class TokenSupplyTypeEnumTest {

    @Test
    void fromId() {
        assertThat(TokenSupplyTypeEnum.fromId(TokenSupplyType.FINITE_VALUE))
                .isEqualTo(TokenSupplyTypeEnum.FINITE);
        assertThat(TokenSupplyTypeEnum.fromId(TokenSupplyType.INFINITE_VALUE))
                .isEqualTo(TokenSupplyTypeEnum.INFINITE);
        assertThat(TokenSupplyTypeEnum.fromId(-1))
                .isEqualTo(TokenSupplyTypeEnum.INFINITE);
    }
}
