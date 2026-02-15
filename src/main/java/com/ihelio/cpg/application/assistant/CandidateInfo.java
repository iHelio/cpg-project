/*
 * Copyright 2026 ihelio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihelio.cpg.application.assistant;

import java.time.Instant;

/**
 * Information about a candidate in the onboarding process.
 *
 * @param candidateId unique identifier for the candidate
 * @param candidateName full name of the candidate
 * @param email candidate's email address
 * @param position the position being hired for
 * @param department the department
 * @param hiringManager the hiring manager's identifier
 * @param startDate expected start date
 */
public record CandidateInfo(
    String candidateId,
    String candidateName,
    String email,
    String position,
    String department,
    String hiringManager,
    Instant startDate
) {
    /**
     * Creates a CandidateInfo with required fields only.
     */
    public static CandidateInfo of(String candidateId, String candidateName) {
        return new CandidateInfo(candidateId, candidateName, null, null, null, null, null);
    }
}
