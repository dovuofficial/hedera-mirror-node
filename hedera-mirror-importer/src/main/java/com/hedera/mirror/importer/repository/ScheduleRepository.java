package com.hedera.mirror.importer.repository;

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

import java.util.Optional;
import javax.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Schedule;

public interface ScheduleRepository extends CrudRepository<Schedule, Long> {
    @Modifying
    @Transactional
    @Query("update Schedule set executedTimestamp = :timestamp where scheduleId = :schedule")
    void updateExecutedTimestamp(@Param("schedule") EntityId scheduleId, @Param("timestamp") long executedTimestamp);

    Optional<Schedule> findByScheduleId(EntityId scheduleId);
}
