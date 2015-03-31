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
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoArrayGenerator extends AbstractPojoTypeGenerator {
	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		// In the easy case we just have type=array, items=SCHEMA, which means we produce a List<SCHEMA-TYPE> reference
		// In other cases we might have to also produce a class extending AbstractList implementing the restrictions given
		// XXX: for now we just basically ignore the other restrictions
		if (schema.getNode().hasNonNull("items")) {
			SchemaTree itemsSchema = schema.append(JsonPointer.of("items"));
			URI elementUri = schema.getLoadingRef().toURI().resolve("#" + itemsSchema.getPointer().toString());;
			AtomicReference<ClassName> elementClassName = new AtomicReference<>();
			visitSchema(elementUri, itemsSchema, new SchemaVisitor<CodeGenerationException>() {
				@Override
				public void visitSchema(URI type, SchemaTree schema) throws CodeGenerationException {
					visitSchema(type);
				}

				@Override
				public void visitSchema(URI type) throws CodeGenerationException {
					elementClassName.set(context.getGenerator().generate(type));
				}
			});
			
			if (elementClassName.get() == null) {
				throw new CodeGenerationException(context.getType(), "Unknown element type: cannot create array type");
			}
			
			return ClassName.create(List.class, elementClassName.get());
		}
		
		return super.generate(context, schema, writer);
	}
	
	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		throw new CodeGenerationException(context.getType(), new UnsupportedOperationException("Cannot generate for non-items-based arrays"));
	}
}
