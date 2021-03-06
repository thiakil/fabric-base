/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.base.loader;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.*;
import net.fabricmc.api.Side;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModInfo {

	//	Required
	private String id;
	private String group;
	private Version version;

	//	Optional
	private String modClass = "";
	private String languageAdapter = "net.fabricmc.base.loader.language.JavaLanguageAdapter";
	private Side side = Side.UNIVERSAL;
	private boolean lazilyLoaded = false;
	private String title = "";
	private String description = "";
	private Links links = Links.EMPTY;
	private DependencyMap dependencies = new DependencyMap();
	private Person[] authors = new Person[0];
	private Person[] contributors = new Person[0];
	private String license = "";

	public String getId() {
		return id;
	}

	public String getGroup() {
		return group;
	}

	public Version getVersion() {
		return version;
	}

	public String getModClass() {
		return modClass;
	}

	public String getLanguageAdapter() {
		return languageAdapter;
	}

	public Side getSide() {
		return side;
	}

	public boolean isLazilyLoaded() {
		return lazilyLoaded;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Links getLinks() {
		return links;
	}

	public DependencyMap getDependencies() {
		return dependencies;
	}

	public Person[] getAuthors() {
		return authors;
	}

	public Person[] getContributors() {
		return contributors;
	}

	public String getLicense() {
		return license;
	}

	public static class Links {

		public static final Links EMPTY = new Links();

		private String homepage = "";
		private String issues = "";
		private String sources = "";

		public String getHomepage() {
			return homepage;
		}

		public String getIssues() {
			return issues;
		}

		public String getSources() {
			return sources;
		}
	}

	public static class DependencyMap extends HashMap<String, Dependency> {

	}

	public static class Dependency {

		private String[] versionMatchers;
		private boolean required;
		private Side side;

		public Dependency(String[] versionMatchers, boolean required, Side side) {
			this.versionMatchers = versionMatchers;
			this.required = required;
			this.side = side;
		}

		public String[] getVersionMatchers() {
			return versionMatchers;
		}

		public boolean isRequired() {
			return required;
		}

		public Side getSide() {
			return side;
		}

		public boolean satisfiedBy(ModInfo info) {
			if (required) {
				for (String s : versionMatchers) {
					if (!info.version.satisfies(s)) {
						return false;
					}
				}
			}
			return true;
		}

		public static class Deserializer implements JsonDeserializer<Dependency> {

			@Override
			public Dependency deserialize(JsonElement element, Type resultType, JsonDeserializationContext context) throws JsonParseException {
				if (element.isJsonObject()) {
					JsonObject object = element.getAsJsonObject();

					String[] versionMatchers;
					boolean required = true;
					Side side = Side.UNIVERSAL;

					if (object.has("required")) {
						JsonElement requiredEl = object.get("required");
						if (requiredEl.isJsonPrimitive() && requiredEl.getAsJsonPrimitive().isBoolean()) {
							required = requiredEl.getAsBoolean();
						} else {
							throw new JsonParseException("Expected required to be a boolean");
						}
					}

					if (object.has("side")) {
						JsonElement sideEl = object.get("side");
						side = context.deserialize(sideEl, Side.class);
					}

					if (object.has("version")) {
						JsonElement versionEl = object.get("version");
						if (versionEl.isJsonPrimitive()) {
							versionMatchers = new String[] { versionEl.getAsString() };
						} else if (versionEl.isJsonArray()) {
							JsonArray array = versionEl.getAsJsonArray();
							versionMatchers = new String[array.size()];
							for (int i = 0; i < array.size(); i++) {
								versionMatchers[i] = array.get(i).getAsString();
							}
						} else {
							throw new JsonParseException("Expected version to be a string or array");
						}
					} else {
						throw new JsonParseException("Missing version element");
					}

					return new Dependency(versionMatchers, required, side);
				}
				throw new JsonParseException("Expected dependency to be an object");
			}

		}
	}

	public static class Person {

		private String name;
		private String email;
		private String website;

		public Person(String name, String email, String website) {
			this.name = name;
			this.email = email;
			this.website = website;
		}

		public String getName() {
			return name;
		}

		public String getEmail() {
			return email;
		}

		public String getWebsite() {
			return website;
		}

		public static class Deserializer implements JsonDeserializer<Person> {

			private static final Pattern WEBSITE_PATTERN = Pattern.compile("\\((.+)\\)");
			private static final Pattern EMAIL_PATTERN = Pattern.compile("<(.+)>");

			@Override
			public Person deserialize(JsonElement element, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
				if (element.isJsonPrimitive()) {
					String person = element.getAsString();
					List<String> parts = Arrays.asList(person.split(" "));

					String name, email = "", website = "";

					Matcher websiteMatcher = WEBSITE_PATTERN.matcher(parts.get(parts.size() - 1));
					if (websiteMatcher.matches()) {
						website = websiteMatcher.group(1);
						parts.remove(parts.size() - 1);
					}

					Matcher emailMatcher = EMAIL_PATTERN.matcher(parts.get(parts.size() - 1));
					if (emailMatcher.matches()) {
						email = emailMatcher.group(1);
						parts.remove(parts.size() - 1);
					}

					name = String.join(" ", parts);

					return new Person(name, email, website);
				}
				throw new RuntimeException("Expected person to be a string");
			}

		}

	}

}
