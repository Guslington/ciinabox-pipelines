/***********************************
 ecr DSL
 Triggers, Waits and displays results of a ECR image scan
 example usage
 ecrScan(
   accountId: '1234567890',
   region: 'ap-southeast-2',
   image: 'myrepo/image',
   tag: 'latest',
   trigger: true | false,
   failOn: 'INFORMATIONAL'|'LOW'|'MEDIUM'|'HIGH'|'CRITICAL'|'UNDEFINED'
 )
 ************************************/

@Grab(group='com.amazonaws', module='aws-java-sdk-ecr', version='1.11.661')

import com.amazonaws.services.ecr.*
import com.amazonaws.services.ecr.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  
  if (config.trigger) {
    triggerScan(ecr,config)
  }
  
}

@NonCPS
def triggerScan(ecr,config) {
  def imageId = new ImageIdentifier().withImageTag(config.tag)
  ecr.startImageScan(new StartImageScanRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageId(imageId) 
  )
}

@NonCPS
def setupClient(region) {
  return AmazonECRClientBuilder.standard()
    .withRegion(region)
    .build()
}