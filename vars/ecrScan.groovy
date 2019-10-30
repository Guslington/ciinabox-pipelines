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
  def results = getScanResults(ecr,config)
  displayScanResults(results)
  failOnSeverity(results,config)
}

@NonCPS
def failOnSeverity(results,config) {
  def severityCount = results.getImageScanFindings().getFindingSeverityCounts()
  def failOn = config.get('failOn','CRITICAL')

  switch(failOn) {
    case 'UNDEFINED':
      severityOrder = ['UNDEFINED','INFORMATIONAL','LOW','MEDIUM','HIGH','CRITICAL']
      break
    case 'INFORMATIONAL':
      severityOrder = ['INFORMATIONAL','LOW','MEDIUM','HIGH','CRITICAL']
      break
    case 'LOW':
      severityOrder = ['LOW','MEDIUM','HIGH','CRITICAL']
      break
    case 'MEDIUM':
      severityOrder = ['MEDIUM','HIGH','CRITICAL']
      break
    case 'HIGH':
      severityOrder = ['HIGH','CRITICAL']
      break
    case 'CRITICAL':
      severityOrder = ['CRITICAL']
      break
  }

  severityOrder.each { severity ->
    if (severityCount.containsKey(severity)) {
      throw new GroovyRuntimeException("image scan found ${severityCount[severity]} severity ${severity} vulnerabilities. Exiting...")
    }
  }
}

@NonCPS
def displayScanResults(ecr,config) {
  def findings = results.getImageScanFindings().getFindings()
  println "\n========================================================================="
  println "## Scan Results                                                        ##"
  println "========================================================================="
  findings.each {
    println "Severity: ${it.severity} Name: ${it.name} Package: ${it.attributes[1].value} Version: ${it.attributes[0].value}"
  }
  println "=========================================================================\n"
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