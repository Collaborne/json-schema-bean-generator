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
import java.util.ArrayList;
import java.util.List;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.tree.SchemaTree;

abstract class AbstractPojoTypeGenerator implements PojoTypeGenerator {
	protected interface SchemaVisitor<T extends Exception> {
		void visitSchema(URI type, SchemaTree schema) throws T;
		void visitSchema(URI type) throws T;
	}
	
	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		ClassName className = context.getMapping().getGeneratedClassName();
		writer.writePackage(className);

		generateType(context, schema, writer);
		
		return className;
	}
	
	protected abstract void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException;
	
	// XXX: do we need elementUri here, schema knows where it came from.
	protected <T extends Exception> boolean visitSchema(URI elementUri, SchemaTree schema, SchemaVisitor<T> visitor) throws T {
		JsonNode element = schema.getNode();
		if (!element.isContainerNode()) {
			return false;
		}
		
		// We handle "id" here in the same way as the json-schema-validator: it's fairly non-trust-worthy.
		// In a properly written document the id is useless, as we already had that with a $ref, and you cannot just modify things in place.
		if (element.hasNonNull("$ref")) {
			// A reference to something else.
			String refValue = element.get("$ref").textValue();
			visitor.visitSchema(elementUri.resolve(refValue));
		} else {
			visitor.visitSchema(elementUri, schema);
		}
		
		return true;
	}

	/**
	 * Write javadoc if the {@code schema} contains {@code title} and/or {@code description} information.
	 *
	 * @param schema
	 * @param writer
	 * @throws IOException
	 */
	protected void writeSchemaDocumentation(SchemaTree schema, JavaWriter writer) throws IOException {
		String title = null;
		String description = null;
		if (schema.getNode().hasNonNull("title")) {
			title = schema.getNode().get("title").textValue();
		}
		if (schema.getNode().hasNonNull("description")) {
			description = schema.getNode().get("description").textValue();
		}
		if (title != null || description != null) {
			// Produce some documentation
			List<String> lines = new ArrayList<>();
			if (title != null) {
				lines.add(title);
				if (description != null) {
					lines.add("");
				}
			}
			if (description != null) {
				for (String descriptionLine : description.split("\n")) {
					lines.add(descriptionLine);
				}
			}
			writer.writeJavadoc(lines.toArray(new String[lines.size()]));
		}
	}
}