#!/usr/bin/env groovy

setProjectProperties()

env.AWS_ECR_URL = "090667427149.dkr.ecr.eu-west-1.amazonaws.com"
env.AWS_ECS_REPOSITORY = "kafka-connect/${params.PROJECT}"
env.ACCOUNT_ID = getAccountID(params.ENVIRONMENT)
env.VERSION = "1.4.0"

try {
  node {
    stage ('Configure | ' + params.ENVIRONMENT + '-' + env.AWS_ECS_REPOSITORY) {
      println("Stage configure")
      deleteDir()
    }

    stage("GIT | Checkout") {
      println("Stage checkout")

      try {
        git branch: 'master', url: "git@code.gniinnova.com:sofia/kafka-connect/${params.PROJECT}.git"
        def commit = sh(script: 'git rev-parse HEAD', returnStdout: true)?.trim()
        def tagCommitStatus = sh(script: "git describe --tags ${commit} > desc.txt", returnStatus: true)
        def desc = readFile("desc.txt").trim()
        env.VERSION = (tagCommitStatus == 0 && isTag(desc)) ? desc : env.VERSION + "-" + env.BUILD_ID + "-SNAPSHOT"
        def getLastTag = sh(script: "git describe --tags --abbrev=0 ${commit}", returnStdout: true)?.trim()
        def versionDynamodb = (params.ENVIRONMENT == 'dev') ? "latest" : getLastTag
        def repoDynamodb = env.AWS_ECS_REPOSITORY + ":" + versionDynamodb
        sh "sed -i 's@__ECR_URL__@${repoDynamodb}@g' template.json"
      } catch (Exception e) {
        setResultPipeline("FAILURE", env.STAGE_NAME, e.toString())
        error e.toString()
      }
    }

    stage ('DOCKER | Build') {
      if (params.ENVIRONMENT == 'dev') {
        try {
          withAWS(role: 'devops-adnsofia-delegated-psf', roleAccount: "090667427149", credentials:'SOFIA_AWS_CREDENTIALS') {
            sh 'docker build --build-arg APP_VERSION=' + env.VERSION + ' -t ' + env.AWS_ECS_REPOSITORY + ':latest .'
            sh 'eval $(aws ecr get-login --no-include-email --region eu-west-1)'
            sh 'docker tag ' + env.AWS_ECS_REPOSITORY + ':latest ' + env.AWS_ECR_URL + '/' + env.AWS_ECS_REPOSITORY + ':' + env.VERSION
            sh 'docker tag ' + env.AWS_ECS_REPOSITORY + ':latest ' + env.AWS_ECR_URL + '/' + env.AWS_ECS_REPOSITORY + ':latest'
            sh 'docker push ' + env.AWS_ECR_URL + '/' + env.AWS_ECS_REPOSITORY + ':' + env.VERSION
            sh 'docker push ' + env.AWS_ECR_URL + '/' + env.AWS_ECS_REPOSITORY + ':latest'
          }
        } catch (Exception e) {
          setResultPipeline("FAILURE", env.STAGE_NAME, e.toString())
          error e.toString()
        }
      }
    }

    stage ('AWS | Put DynamoDB') {
      try {
        withAWS(role: params.ENVIRONMENT + '-adnsofia-delegated-devops', roleAccount: env.ACCOUNT_ID, credentials:'SOFIA_AWS_CREDENTIALS') {
          sh "aws dynamodb put-item --table-name EngineTypes --item file://template.json --region=eu-west-1"
        }
        setResultPipeline("SUCCESS", env.STAGE_NAME, 'Deploy success')
      } catch (Exception e) {
        setResultPipeline("FAILURE", env.STAGE_NAME, e.toString())
        error e.toString()
      }
    }
  }
} catch (e) {
  error e.toString()
} finally {
  if (params.ENVIRONMENT == "pro" || params.ENVIRONMENT == "pre") {
      sendNotification(params.ENVIRONMENT, env.stagePipe, currentBuild.result, env.message)
  }
}

/*
  Método para establecer estados de ejecucion por fase del pipeline.
  @param String result: resultado de la ejecución de la fase.
  @param String stagePipe: Fase del pipeline.
  @param String message: Texto descriptivo de la ejecucion.
*/
def setResultPipeline(String result, String stagePipe, String message) {
  currentBuild.result = result
  env.stagePipe = stagePipe
  env.message = message
}

/*
  Método para establecer las propiedades del proyecto
*/
def setProjectProperties() {
    def projectProperties = [buildDiscarder(logRotator(artifactDaysToKeepStr: '',artifactNumToKeepStr: '',daysToKeepStr: '',numToKeepStr: '15'))]
    properties(projectProperties)
}

/*
  Función para envío de notificación mediante correo electrónico.
  @param String enviroment: Entorno de ejecución.
  @param String stage: Fase del pipeline.
  @param String currentBuildAux: Estado de la ejecución de fase.
  @param String message: Mensaje para cuerpo de correo electrónico.
  @state: Resultado de la ejecución.
*/
def sendNotification(String enviroment, String stage, String currentBuildAux, String message) {

  def state = false
  try {
    emailext (
	      to: "ignacio.hidalgo@bluetab.net",
        subject: "CI/CD sofia-" + enviroment + ": Job ${env.JOB_NAME} [${env.BUILD_NUMBER}] :: " + stage + " :: " + currentBuildAux,
        body: message,
        recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
    )
    state = true
  } catch (Exception e) {
    println("Notification aborted")
  }
  return state
}

boolean isTag(String desc) {
    match = desc =~ /.+-[0-9]+-g[0-9A-Fa-f]{6,}$/
    result = !match
    match = null // prevent serialisation
    return result
}

def getAccountID(String environment) {
  def account
  switch (environment) {
      case "pro":
          account = "224612757426"
          break
      case "pre":
          account = "930582514773"
          break
      case "dev":
          account = "655251547688"
          break
      default:
          account = null
  }
  return account
}