/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.core.rpc.utils.mate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Maps;

public class ObjectScope<T> implements Scope {
    private final ScopeName scopeName;
    private final T object;
    private final Map<ContextKey, Function<T, Optional<String>>> accessors = Maps.newHashMap();

    public ObjectScope(final ScopeName scopeName, final T object) {
        this.scopeName = Objects.requireNonNull(scopeName);
        this.object = Objects.requireNonNull(object);
    }

    @Override
    public Optional<ScopeValue> get(final ContextKey contextKey) {
        return this.accessors.getOrDefault(contextKey, (missing) -> Optional.empty())
                .apply(this.object)
                .map(value -> new ScopeValue(this.scopeName, value));
    }

    @Override
    public Set<ContextKey> keys() {
        return this.accessors.keySet();
    }

    public ObjectScope<T> map(final String context, final String key, final Function<T, Optional<String>> accessor) {
        this.accessors.put(new ContextKey(context, key), accessor);
        return this;
    }
}
