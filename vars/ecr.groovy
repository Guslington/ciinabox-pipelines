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
   tags: [ key: value ] # key value map of tags
 )
 ************************************/

@Grab(group='com.amazonaws', module='aws-java-sdk-ecr', version='1.11.482')

import com.amazonaws.services.ecr.*
import com.amazonaws.services.ecr.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  createRepo(ecr,config.image)
  addTags(ecr,config)
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
def setupClient(region) {
  return AmazonECRClientBuilder.standard()
    .withRegion(region)
    .build()
}

@NonCPS
def addTags(ecr,config) {
  List<Tag> tags = new ArrayList<Tag>()
  tags.add(new Tag().setKey('Name').setValue(config.image))
  tags.add(new Tag().setKey('CreatedBy').setValue('ciinabox-pipelines')s)
  if (config.containsKey('tags')) {
    config.tags.each { k,v -> tags.add(new Tag().setKey(k).setValue(v))) }
  }
  ecr.tagResource(new TagResourceRequest()
    .withTags(tags)
  )
}
