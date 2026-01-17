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

/**
 * Domain layer containing core business logic.
 *
 * <p>This package contains:
 * <ul>
 *   <li>Entities - Core domain objects with identity</li>
 *   <li>Value Objects - Immutable objects defined by their attributes</li>
 *   <li>Domain Services - Business logic that doesn't belong to a single entity</li>
 *   <li>Ports (Interfaces) - Contracts for infrastructure adapters</li>
 *   <li>Domain Events - Events that represent domain state changes</li>
 * </ul>
 *
 * <p>This layer has no dependencies on other layers and defines the core
 * business rules of the application following DDD principles.
 */
package com.ihelio.cpg.domain;
