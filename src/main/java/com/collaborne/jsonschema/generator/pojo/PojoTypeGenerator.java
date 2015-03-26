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
package com.collaborne.jsonschema.generator.pojo;

import java.io.IOException;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.github.fge.jsonschema.core.tree.SchemaTree;

interface PojoTypeGenerator {
	/**
	 * Generate contents for the given {@code schema} using the provided mapping.
	 * 
	 * @param context TODO
	 * @param schema
	 * @param javaWriter
	 * @return TODO
	 * @throws IOException
	 * @throws CodeGenerationException 
	 */
	ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter javaWriter) throws IOException, CodeGenerationException;
}