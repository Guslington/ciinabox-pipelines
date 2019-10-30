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

@Grab(group='com.amazonaws', module='aws-java-sdk-ecr', version='1.11.662')

import com.amazonaws.services.ecr.*
import com.amazonaws.services.ecr.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  
  if (config.trigger) {
    triggerScan(ecr,config)
  }
  
  waitForScanResults(ecr,config)
  displayScanResults(ecr, config)
}

@NonCPS
def displayScanResults(ecr,config) {
  ImageScanFindings findings = getScanResults(ecr,config).getImageScanFindings()
}

@NonCPS
def waitForScanResults(ecr,config) {
  def status = getScanResults(ecr,config)
    .getImageScanStatus().getStatus()
  while(status == 'IN_PROGRESS') {
    println 'waiting for image scan to complete'
    Thread.sleep(5000)
    status = getScanResults(ecr,config)
      .getImageScanStatus().getStatus()
    if (status == 'COMPLETE') {
      println 'image scan completed'
      break
    } else {
      throw new Exception("image scan failed to complete. ${status}")
    }
  }
}

@NonCPS
def getScanResults(ecr,config) {
  def imageId = new ImageIdentifier().withImageTag(config.tag)
  def request = new DescribeImageScanFindingsRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageId(imageId) 
  DescribeImageScanFindingsResult result = ecr.describeImageScanFindings(request)
  return result
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