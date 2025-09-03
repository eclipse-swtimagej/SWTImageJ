pipeline {
	agent {
		label 'ubuntu-latest'
	}
	triggers {
		githubPush()
	}
	options {
		disableConcurrentBuilds()
	}
	tools {
		maven 'apache-maven-latest'
		jdk   'temurin-jdk21-latest'
	}
	stages {
		stage('Build') {
			steps {
				sh "mvn -Peclipse-sign clean install"
			}
		}
		stage('Deploy') {
			steps {
				sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
					sh '''
						ssh genie.swtimagej@projects-storage.eclipse.org rm -rf /home/data/httpd/download.eclipse.org/swtimagej/integration/${BRANCH_NAME}
						ssh genie.swtimagej@projects-storage.eclipse.org mkdir -p /home/data/httpd/download.eclipse.org/swtimagej/integration/${BRANCH_NAME}/repository
						scp -r org.eclipse.swt.imagej.updatesite/target/repository/* genie.swtimagej@projects-storage.eclipse.org:/home/data/httpd/download.eclipse.org/swtimagej/integration/${BRANCH_NAME}/repository
					'''
				}
			}
		}
	}
}
