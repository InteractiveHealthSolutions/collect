# ODKATE
========
Th project aims to integrate ODK-Collect app by OpenDataKit [https://opendatakit.org](https://opendatakit.org) as library with any other application.

ODK Collect renders forms that are compliant with the [ODK XForms standard](http://opendatakit.github.io/xforms-spec/), a subset of the [XForms 1.1 standard](https://www.w3.org/TR/xforms/) with some extensions. The form parsing is done by the [JavaRosa library](https://github.com/opendatakit/javarosa) which Collect includes as a jar.

How to use:

- Get the latest release
- Extract the folder in your project`s root folder
- Add following 'maven { url "$rootDir/repo/" }' to main project`s build.gradle like below
	allprojects {
		repositories {
			maven { url "$rootDir/repo/" } 
		}
	}
- Include the dependency as below to your module where Odkate integration is required
	compile ('com.ihs.odkate:odkate-base:1.0.0@aar') { 
		transitive=true 
		exclude group: 'com.google.guava'
	}
- If you want Odkate automatically generate bind_type for your form_definition.json add attribute bind_type="xxx" in main node or repeats