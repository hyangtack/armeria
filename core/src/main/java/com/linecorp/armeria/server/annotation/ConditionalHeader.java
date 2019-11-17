/*
 * Copyright 2019 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.linecorp.armeria.server.RouteBuilder;

/**
 * Specifies a predicate which evaluates whether a request can be accepted by a service method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConditionalHeaders.class)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface ConditionalHeader {
    /**
     * The predicate which evaluates whether a request can be accepted by a service method.
     *
     * @see RouteBuilder#matchesHeaderPredicates(CharSequence...)
     * @see RouteBuilder#matchesHeaderPredicates(Iterable)
     */
    String value();
}
