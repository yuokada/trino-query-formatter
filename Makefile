# Description: Makefile for building the project with GraalVM (Experimental)
GRAALVM_VERSION=graalvm-22.3.3+java17

build_with_graalvm:
	asdf local java $(GRAALVM_VERSION)
	export GRAALVM_HOME=`asdf where java`
	./mvnw -Pnative clean package -DskipTests

install_graalvm:
	asdf install java $(GRAALVM_VERSION)
