pipeline {
	agent {
		kubernetes {
			label 'ubuntu-latest'
		}
	}
	triggers {
		pollSCM('H/5 * * * *')
	}
	options {
		disableConcurrentBuilds()
	}
	tools {
		maven 'apache-maven-latest'
		jdk   'temurin-jdk17-latest'
	}
	stages {
		stage('Build') {
			steps {
				sh "mvn -f org.eclipse.swt.imagej.cbi/pom.xml -Peclipse-sign clean install"
			}
		}
	}
}
