/***********************************
 samDeploy DSL

 Packages sam template and pushes artefacts to S3

 example usage
 samDeploy(
   region: env.AWS_REGION,
   stackName: dev,
   template: template.yaml,
   source_bucket: source.bucket,
   prefix: cloudformation/${PROJECT}/${BRANCH_NAME}/${BUILD_NUMBER}
   parameters: [
     'ENVIRONMENT_NAME' : 'dev',
   ]
   accountId: '1234567890' #the aws account Id you want the stack operation performed in
   role: 'myrole' # the role to assume from the account the pipeline is running from
 )
 ************************************/

def call(body) {
  def config = body
  def compiled_template = config.template.replace(".yaml", "-compiled.yaml")
  def params = ""

  println("Copying s3://${config.source_bucket}/${config.prefix}/${compiled_template} to local")

  sh "aws s3 cp s3://${config.source_bucket}/${config.prefix}/${compiled_template} ${compiled_template}"

  if (parameters != null || !parameters.empty) {
    params = "--parameter-overrides"
    config.parameters.each {
      params = params.concat(" ${it.key}=${it.value}")
    }
  }

  println("deploying ${compiled_template} to environment ${config.environment}")

  withIAMRole(config.accountId,config.region,config.role) {
    sh """
    #!/bin/bash
    aws cloudformation deploy \
      --template-file ${compiled_template} \
      --s3-bucket ${config.source_bucket} \
      --s3-prefix ${config.prefix} \
      --stack-name ${stackName} \
      ${params}
      --capabilities CAPABILITY_IAM
    """
  }
}
