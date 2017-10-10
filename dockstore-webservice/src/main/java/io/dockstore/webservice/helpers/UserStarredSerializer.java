/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.dockstore.webservice.core.Entry;

/**
 * Created by aduncan on 19/12/16.
 */
public class UserStarredSerializer extends StdSerializer<Set<Entry>> {

    public UserStarredSerializer() {
        this(null);
    }

    public UserStarredSerializer(Class<Set<Entry>> t) {
        super(t);
    }

    @Override
    public void serialize(Set<Entry> value, JsonGenerator jgen, SerializerProvider provider) throws IOException {

        jgen.writeStartArray();
        for (Entry entry : value) {
            jgen.writeStartObject();
            jgen.writeNumberField("id", entry.getId());
            jgen.writeEndObject();
        }
        jgen.writeEndArray();
    }
}
