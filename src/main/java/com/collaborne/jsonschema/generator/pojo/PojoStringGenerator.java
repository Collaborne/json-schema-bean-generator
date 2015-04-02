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
import java.util.Locale;
import java.util.Objects;

import com.collaborne.jsonschema.generator.CodeGenerationException;
import com.collaborne.jsonschema.generator.java.ClassName;
import com.collaborne.jsonschema.generator.java.JavaWriter;
import com.collaborne.jsonschema.generator.java.Kind;
import com.collaborne.jsonschema.generator.java.Visibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.tree.SchemaTree;

public class PojoStringGenerator extends AbstractPojoTypeGenerator {
	private interface EnumGenerator {
		void generateEnumValue(String value, JavaWriter writer) throws IOException;
		void generateAdditionalCode(JavaWriter writer) throws IOException;
	}

	private static abstract class AbstractEnumGenerator implements EnumGenerator {
		private final ClassName className;
		private final Visibility constructorVisibility;

		protected AbstractEnumGenerator(ClassName className, Visibility constructorVisibility) {
			this.className = className;
			this.constructorVisibility = constructorVisibility;
		}

		public ClassName getClassName() {
			return className;
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			ClassName stringClassName = ClassName.create(String.class);

			// XXX: field should be final
			writer.writeEmptyLine();
			writer.writeField(Visibility.PRIVATE, stringClassName, "value");

			// Create the constructor
			writer.writeConstructorBodyStart(constructorVisibility, getClassName(), stringClassName, "value");
			writer.writeCode("this.value = value;");
			writer.writeMethodBodyEnd();

			// Create an accessor for the value
			writer.writeMethodBodyStart(Visibility.PUBLIC, stringClassName, "getValue");
			writer.writeCode("return value;");
			writer.writeMethodBodyEnd();

			// Create a nice #toString() that uses the value
			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, stringClassName, "toString");
			writer.writeCode("return getValue();");
			writer.writeMethodBodyEnd();
		}
	}

	private static class ClassEnumGenerator extends AbstractEnumGenerator {
		public ClassEnumGenerator(ClassName className) {
			// XXX: Visibility of the constructor should somehow get linked to the additionalProperties or such?
			super(className, Visibility.PUBLIC);
		}

		@Override
		public void generateEnumValue(String value, JavaWriter writer) throws IOException {
			writer.writeCode("public static " + getClassName().getRawClassName() + " " + value.toUpperCase(Locale.ENGLISH) + " = new " + getClassName().getRawClassName() + "(\"" + value + "\");");
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			super.generateAdditionalCode(writer);

			// Create #hashCode() and #equals()
			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, ClassName.create(Integer.TYPE), "hashCode");
			writer.writeCode("return Objects.hash(value);");
			writer.writeMethodBodyEnd();

			writer.writeAnnotation(ClassName.create(Override.class));
			writer.writeMethodBodyStart(Visibility.PUBLIC, ClassName.create(Boolean.TYPE), "equals", ClassName.create(Object.class), "obj");
			writer.writeCode(
					"if (!(obj instanceof " + getClassName().getRawClassName() + ")) {",
					"\treturn false;",
					"}",
					"return Objects.equals(value, ((" + getClassName().getRawClassName() + ") obj).value);");
			writer.writeMethodBodyEnd();
		}
	}

	private static class EnumEnumGenerator extends AbstractEnumGenerator {
		private boolean wroteOneValue = false;

		public EnumEnumGenerator(ClassName className) {
			super(className, Visibility.PRIVATE);
		}

		@Override
		public void generateEnumValue(String value, JavaWriter writer) throws IOException {
			if (wroteOneValue) {
				writer.write(",\n");
			}
			writer.writeIndent();
			writer.write(value.toUpperCase(Locale.ENGLISH) + "(\"" + value + "\")");
			wroteOneValue = true;
		}

		@Override
		public void generateAdditionalCode(JavaWriter writer) throws IOException {
			assert wroteOneValue : "Invalid code generated: Missing enum values";
			if (wroteOneValue) {
				writer.write(";\n");
			}

			super.generateAdditionalCode(writer);
		}
	}

	@Override
	public ClassName generate(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		if (!schema.getNode().hasNonNull("enum")) {
			// Not an enum-ish string, so just map it to that.
			return ClassName.create(String.class);
		}

		return super.generate(context, schema, writer);
	}

	@Override
	protected void generateType(PojoCodeGenerationContext context, SchemaTree schema, JavaWriter writer) throws IOException, CodeGenerationException {
		JsonNode enumValues = schema.getNode().get("enum");
		if (!enumValues.isArray()) {
			throw new CodeGenerationException(context.getType(), "Expected 'array' for 'enum', but have " + enumValues);
		}

		EnumGenerator enumGenerator;
		Kind enumStyle = context.getGenerator().getFeature(PojoGenerator.FEATURE_ENUM_STYLE);
		switch (enumStyle) {
		case CLASS:
			enumGenerator = new ClassEnumGenerator(context.getMapping().getClassName());
			break;
		case ENUM:
			enumGenerator = new EnumEnumGenerator(context.getMapping().getClassName());
			break;
		default:
			throw new CodeGenerationException(context.getType(), new IllegalArgumentException("Invalid enum style: " + enumStyle));
		}

		writer.writeImport(ClassName.create(Objects.class));

		writeSchemaDocumentation(schema, writer);
		writer.writeClassStart(context.getMapping().getClassName(), enumStyle, Visibility.PUBLIC);
		try {
			for (JsonNode enumValue : enumValues) {
				if (!enumValue.isTextual()) {
					throw new CodeGenerationException(context.getType(), "Expected textual 'enum' values, but have " + enumValue);
				}
				String value = enumValue.textValue();
				enumGenerator.generateEnumValue(value, writer);
			}
			enumGenerator.generateAdditionalCode(writer);
		} finally {
			writer.writeClassEnd();
		}
	}
}
