pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: indezy-build
spec:
  serviceAccountName: jenkins-agent
  containers:
  - name: docker
    image: docker:24-cli
    command:
    - sleep
    args:
    - 99d
    volumeMounts:
    - name: docker-sock
      mountPath: /var/run/docker.sock
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
'''
        }
    }

    environment {
        REGISTRY     = 'nexus.swirlit.dev:5000'
        SERVER_IMAGE = "${REGISTRY}/indezy/indezy-server"
        WEB_IMAGE    = "${REGISTRY}/indezy/indezy-web"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        GIT_REPO_URL = 'https://github.com/chefzaid/indezy.git'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Push Server Image') {
            steps {
                container('docker') {
                    withCredentials([usernamePassword(
                            credentialsId: 'nexus-docker-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASS')]) {
                        sh """
                            echo "\${NEXUS_PASS}" | docker login ${REGISTRY} -u "\${NEXUS_USER}" --password-stdin
                            docker build \\
                                --target production \\
                                -t ${SERVER_IMAGE}:${IMAGE_TAG} \\
                                -t ${SERVER_IMAGE}:latest \\
                                indezy-server/
                            docker push ${SERVER_IMAGE}:${IMAGE_TAG}
                            docker push ${SERVER_IMAGE}:latest
                            docker rmi ${SERVER_IMAGE}:${IMAGE_TAG} ${SERVER_IMAGE}:latest || true
                        """
                    }
                }
            }
        }

        stage('Build & Push Web Image') {
            steps {
                container('docker') {
                    withCredentials([usernamePassword(
                            credentialsId: 'nexus-docker-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASS')]) {
                        sh """
                            docker build \\
                                --target production \\
                                -t ${WEB_IMAGE}:${IMAGE_TAG} \\
                                -t ${WEB_IMAGE}:latest \\
                                indezy-web/
                            docker push ${WEB_IMAGE}:${IMAGE_TAG}
                            docker push ${WEB_IMAGE}:latest
                            docker rmi ${WEB_IMAGE}:${IMAGE_TAG} ${WEB_IMAGE}:latest || true
                        """
                    }
                }
            }
        }

        stage('Update Manifests') {
            steps {
                sh """
                    sed -i 's|image: ${SERVER_IMAGE}:.*|image: ${SERVER_IMAGE}:${IMAGE_TAG}|g' deployments/indezy-server.yaml
                    sed -i 's|image: ${WEB_IMAGE}:.*|image: ${WEB_IMAGE}:${IMAGE_TAG}|g' deployments/indezy-web.yaml
                """
                withCredentials([gitUsernamePassword(
                        credentialsId: 'git-credentials',
                        gitToolName: 'Default')]) {
                    sh """
                        git config user.email "jenkins@swirlit.dev"
                        git config user.name "Jenkins CI"
                        git add deployments/indezy-server.yaml deployments/indezy-web.yaml
                        git diff --cached --quiet || git commit -m "ci: bump image tags to ${IMAGE_TAG} [skip ci]"
                        git pull --rebase origin HEAD
                        git push origin HEAD
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Build ${IMAGE_TAG} completed successfully. ArgoCD will sync the new manifests."
        }
        failure {
            echo "Build ${IMAGE_TAG} failed."
        }
        always {
            container('docker') {
                sh 'docker logout ${REGISTRY} || true'
            }
        }
    }
}
