# Description: Makefile for building the project with GraalVM (Experimental)
GRAALVM_VERSION=graalvm-community-21.0.2

build_with_graalvm:
	asdf set java $(GRAALVM_VERSION)
	export GRAALVM_HOME=`asdf where java`
	./mvnw -Pnative clean package -DskipTests

install_native_image:
	asdf exec gu install native-image

install_graalvm:
	asdf install java $(GRAALVM_VERSION)
