/***********************************
 ecr DSL
 Creates ECR repository
 example usage
 ecr(
   accountId: '1234567890',
   region: 'ap-southeast-2',
   image: 'myrepo/image',
   otherAccountIds: ['0987654321','5432167890'],
   taggedCleanup: ['master','develop'],
   imutableTags: true | false,
   scanOnPush: true | false
 )
 ************************************/

@Grab(group='com.amazonaws', module='aws-java-sdk-ecr', version='1.11.661')

import com.amazonaws.services.ecr.*
import com.amazonaws.services.ecr.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  createRepo(ecr,config.image)
  if (config.otherAccountIds) {
    setRepositoryPolicy(ecr,config)
  }

  def rules = [
    [
      rulePriority: 100,
      description: "remove all untagged images",
      selection: [
        tagStatus: "untagged",
        countType: "imageCountMoreThan",
        countNumber: 1
      ],
      action: [
        type: "expire"
      ]
    ]
  ]

  if (config.taggedCleanup) {
    rules << [
      rulePriority: 200,
      description: "Keep last 10 ${config.taggedCleanup.join(" ")} builds",
      selection: [
        tagStatus: "tagged",
        countType: "imageCountMoreThan",
        countNumber: 10,
        tagPrefixList: config.taggedCleanup
      ],
      action: [
        type: "expire"
      ]
    ]
  }

  setLifcyclePolicy(ecr,rules,config)
  setImutableTags(ecr,config)
  setScanningConfig(ecr,config)
}

@NonCPS
def createRepo(ecr,repo) {
  try{
    ecr.createRepository(new CreateRepositoryRequest()
      .withRepositoryName(repo)
    )
    println "Created repo ${repo}"
  } catch (RepositoryAlreadyExistsException e) {
    println "${e.getErrorMessage()} for ECR repository ${repo}"
  }
}

@NonCPS
def setRepositoryPolicy(ecr,config) {
  def document = [
    "Version": "2008-10-17",
    "Statement": []
  ]
  config.otherAccountIds.each { accountId ->
    document.Statement << [
      "Sid": "AllowPull",
      "Effect": "Allow",
      "Principal": [
        "AWS": "arn:aws:iam::${accountId}:root"
      ],
      "Action": [
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability"
      ]
    ]
  }
  def builder = new groovy.json.JsonBuilder(document)
  println "Applying ECR access policy\n${builder.toPrettyString()}"
  ecr.setRepositoryPolicy(new SetRepositoryPolicyRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withPolicyText(builder.toString())
  )
}

@NonCPS
def setLifcyclePolicy(ecr,rules,config) {
  def policy = [ rules: rules ]
  def builder = new groovy.json.JsonBuilder(policy)
  println "Applying ECR lifecycle policy\n${builder.toPrettyString()}"
  ecr.putLifecyclePolicy(new PutLifecyclePolicyRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withLifecyclePolicyText(builder.toString())
  )
}

@NonCPS
def setImutableTags(ecr,config) {
  def imutableTags = config.get('imutableTags', false)
  def imutableTagsString = ((imutableTags) ? 'IMMUTABLE' : 'MUTABLE')
  println "Setting image tags on repo to ${tagMutability}"
  ecr.putImageTagMutability(new PutImageTagMutabilityRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageTagMutability(imutableTagsString)
  )
}

@NonCPS
def setScanningConfig(ecr,config) {
  def scanOnPush = new ImageScanningConfiguration().withScanOnPush(config.scanOnPush)
  ecr.putImageScanningConfiguration(new PutImageScanningConfigurationRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageScanningConfiguration(scanOnPush)
  )
}

@NonCPS
def setupClient(region) {
  return AmazonECRClientBuilder.standard()
    .withRegion(region)
    .build()
}
