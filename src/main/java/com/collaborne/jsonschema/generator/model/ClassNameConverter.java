/**
 * Copyright (C) 2015 Collaborne B.V. (opensource@collaborne.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.collaborne.jsonschema.generator.model;

import com.collaborne.jsonschema.generator.java.ClassName;
import com.fasterxml.jackson.databind.util.StdConverter;

public class ClassNameConverter extends StdConverter<String, ClassName> {
	@Override
	public ClassName convert(String value) {
		return ClassName.parse(value);
	}
}
