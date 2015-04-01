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
package com.collaborne.jsonschema.generator.java;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

// TODO: extract interface, this is really the "PrettyJavaWriter"
// TODO: where should the java-awareness lie? Is this not something on top of the purely syntactic writing of java code? And how far should it go?
public class JavaWriter implements Closeable {
	private final BufferedWriter writer;
	// TODO: allow for different indents (like 4 spaces, 2 spaces, etc)
	private String indent = "\t";
	private int indentLevel = 0;
	private String packageName = "";
	private Stack<ClassName> currentClassNames = new Stack<>();
	/** Map of all imports: package.rawClassName to rawClassName */
	private Map<String, String> importedClassNames = new HashMap<>();
	private boolean importsFlushed = false;
	
	public JavaWriter(BufferedWriter writer) {
		this.writer = writer;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}
	
	public void pushIndentLevel() {
		indentLevel++;
	}
	
	public void popIndentLevel() {
		indentLevel--;
	}
	
	public void writeIndent() throws IOException {
		for (int i = 0; i < indentLevel; i++) {
			writer.write(indent);
		}
	}
	
	public void writePackage(ClassName fqcn) throws IOException {
		String packageName = fqcn.getPackageName();
		if (!packageName.isEmpty()) {
			writer.write("package ");
			writer.write(packageName);
			writer.write(";\n");
		}
		// Remember for later
		this.packageName = packageName;
	}
	
	/**
	 * @throws IOException
	 */
	public void writeImport(ClassName fqcn) throws IOException {
		String packageName = fqcn.getPackageName();
		if (packageName.isEmpty()) {
			// Cannot import from the default package
			return;
		}
		if (this.packageName.equals(packageName) || "java.lang".equals(packageName)) {
			// Skip our package and java.lang
			return;
		}
		
		String rawClassName = fqcn.getRawClassName();
		if (!importedClassNames.values().contains(rawClassName)) {
			// Not yet imported, so we can pick this one
			String importClassName = packageName + "." + rawClassName;
			importedClassNames.put(importClassName, rawClassName);
		}

		if (fqcn.getTypeArguments() != null) {
			// Try importing the type arguments as well
			for (ClassName typeArgument : fqcn.getTypeArguments()) {
				writeImport(typeArgument);
			}
		}
	}

	protected void flushImports() throws IOException {
		if (importsFlushed || importedClassNames.isEmpty()) {
			return;
		}

		// Write the imported class names, sorted by name
		List<String> importClassNames = importedClassNames.keySet().stream().sorted().collect(Collectors.toList());
		
		writeEmptyLine();
		for (String importClassName : importClassNames) {
			writeImportForce(ClassName.parse(importClassName));
		}
		importsFlushed = true;
	}
	
	protected String getAvailableShortName(ClassName fqcn) {
		String className = null;
		String packageName = fqcn.getPackageName();
		String rawClassName = fqcn.getRawClassName();
		if (packageName.isEmpty()) {
			className = rawClassName;
		} else if (packageName.equals(this.packageName) || "java.lang".equals(packageName)) {
			// Always available
			className = fqcn.getRawClassName();
		} else {
			String importClassName = packageName + "." + rawClassName;
			className = importedClassNames.get(importClassName);
		}
		
		StringBuilder sb = new StringBuilder();
		if (className == null) {
			sb.append(packageName);
			sb.append(".");
			sb.append(rawClassName);
		} else {
			sb.append(className);
		}
		
		// Add the type arguments
		ClassName[] typeArguments = fqcn.getTypeArguments();
		if (typeArguments != null && typeArguments.length > 0) {
			sb.append("<");
			for (int i = 0; i < typeArguments.length; i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(getAvailableShortName(typeArguments[i]));
			}
			sb.append(">");
		}
		return sb.toString();
	}
	
	public void writeImportForce(ClassName fqcn) throws IOException {
		if (!fqcn.getPackageName().isEmpty()) {
			writer.write("import ");
			writer.write(fqcn.getPackageName());
			writer.write(".");
			writer.write(fqcn.getRawClassName());
			writer.write(";\n");
		}
	}
	
	// XXX: This should get collected as well, and flushed in #flushImports()
	public void writeImport(ClassName fqcn, String methodName) throws IOException {
		writer.write("import static ");
		if (!fqcn.getPackageName().isEmpty()) {
			writer.write(fqcn.getPackageName());
			writer.write(".");
		}
		writer.write(fqcn.getRawClassName());
		writer.write(".");
		writer.write(methodName);
		writer.write(";\n");
	}

	public void writeClassStart(ClassName fqcn, Kind kind, Visibility visibility) throws IOException {
		writeClassStart(fqcn, Collections.emptyList(), Collections.emptyList(), kind, visibility);
	}

	public void writeClassStart(ClassName fqcn, List<ClassName> extendedClasses, List<ClassName> implementedInterfaces, Kind kind, Visibility visibility) throws IOException {
		flushImports();

		// XXX: visibility in the mapping? options ("all public", "all minimum?")
		writeEmptyLine();
		writeIndent();
		writer.write(visibility.getValue());
		writer.write(" ");
		writer.write(kind.getValue());
		writer.write(" ");
		// XXX: generating generic types won't work with just this
		writer.write(fqcn.getRawClassName());

		// Write extended classes, if any
		if (extendedClasses != null && !extendedClasses.isEmpty()) {
			writer.write(" ");
			writer.write("extends");
			writer.write(" ");
			for (int i = 0; i < extendedClasses.size(); i++) {
				if (i > 0) {
					writer.write(", ");
				}
				writeClassName(extendedClasses.get(i));
			}
		}

		// Write implemented interfaces, if any
		if (implementedInterfaces != null && !implementedInterfaces.isEmpty()) {
			writer.write(" ");
			writer.write("implements");
			writer.write(" ");
			for (int i = 0; i < implementedInterfaces.size(); i++) {
				if (i > 0) {
					writer.write(", ");
				}
				writeClassName(implementedInterfaces.get(i));
			}
		}
		writer.write(" {\n");
		pushIndentLevel();
		currentClassNames.push(fqcn);
	}
	
	public void writeClassEnd() throws IOException {
		popIndentLevel();
		writeIndent();
		writer.write("}\n");
		currentClassNames.pop();
	}
	
	public void writeField(Visibility visibility, ClassName className, String fieldName) throws IOException {
		writeIndent();
		writer.write(visibility.getValue());
		writer.write(" ");
		writeClassName(className);
		writer.write(" ");
		writer.write(fieldName);
		writer.write(";\n");
	}

	// FIXME: declaration is really weird, should introduce a dedicated type for (ClassName, String)
	public void writeMethodBodyStart(Visibility visibility, ClassName className, String methodName, Object... typesAndValues) throws IOException {
		assert typesAndValues == null || typesAndValues.length % 2 == 0;
		writeEmptyLine();
		writeIndent();
		writer.write(visibility.getValue());
		writer.write(" ");
		writeClassName(className);
		writer.write(" ");
		writer.write(methodName);
		writer.write("(");
		if (typesAndValues != null) {
			for (int i = 0; i < typesAndValues.length; i += 2) {
				if (i > 0) {
					writer.write(", ");
				}
				writeMethodBodyStartFormalArgument((ClassName) typesAndValues[i], (String) typesAndValues[i + 1]);
			}
		};
		writer.write(") {\n");
		pushIndentLevel();
	}
	
	public void writeCode(String... lines) throws IOException {
		assert lines != null;
		for (String line : lines) {
			writeIndent();
			writer.write(line);
			writer.write("\n");
		}
	}
	
	public void writeMethodBodyEnd() throws IOException {
		popIndentLevel();
		writeIndent();
		writer.write("}\n");
	}
	
	protected void writeMethodBodyStartFormalArgument(ClassName className, String parameterName) throws IOException {
		writeClassName(className);
		writer.write(" ");
		writer.write(parameterName);
	}
	
	/**
	 * Write a class name that can be shortened
	 *  
	 * @param fqcn
	 * @throws IOException 
	 */
	protected void writeClassName(ClassName fqcn) throws IOException {
		String className = getAvailableShortName(fqcn);
		writer.write(className);
	}

	protected void writeEmptyLine() throws IOException {
		writer.write('\n');
	}
}