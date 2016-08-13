package com.example.controller;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.example.entity.Usage;
import com.example.service.ChargeBackService;
import com.example.vo.ChargeBackAggregrateVO;
import com.example.vo.ChargeBackUsageResponse;

@RestController
public class CFMetricsController {
	
	@Autowired
	private ChargeBackService chargebackService;
	

	/**
	 * This Controller fetches the free available resource based on the Resource Type at the Org Level
	 * @param resourceType
	 * @return
	 */
	@RequestMapping(value = "/getFreeResource/{resourceType}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getFreeResource(@PathVariable String resourceType) {

		CloudFoundryClient client = loginCloudFoundry();

		if (resourceType.equals("MEM")) {
			long memoryquota = client.getQuotaByName("trial", true).getMemoryLimit() * 1024 * 1024;

			for (CloudApplication application : client.getApplications()) {
				memoryquota = memoryquota
						- ((client.getApplicationStats(application.getName()).getRecords().get(0).getUsage().getMem()));
			}
			return String.valueOf(memoryquota);
		} else if (resourceType.equals("CPU")) {
			double cpuQuota = 1.0;
			for (CloudApplication application : client.getApplications()) {
				cpuQuota = cpuQuota
						- ((client.getApplicationStats(application.getName()).getRecords().get(0).getUsage().getMem()));
			}
			return String.valueOf(cpuQuota);
		} else {
			// TODO : Not implemented for Disk usage 
			return null;
		}

	}

	@RequestMapping(value = "/getInstanceMetrics", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)	
	public List<ChargeBackUsageResponse> getApplicationInstancesData() {
		
		CloudFoundryClient client = loginCloudFoundry();
		final List<CloudApplication> cloudApplications = client.getApplications();
		final List<CloudSpace> cloudSpaces = client.getSpaces();
		ChargeBackAggregrateVO chargeBackAggregrateVO;
		final List<ChargeBackAggregrateVO> chargeBackAggregrateVOList = new ArrayList<>();
		for(final CloudApplication application : cloudApplications){
			chargeBackAggregrateVO = new ChargeBackAggregrateVO();
			chargeBackAggregrateVO.setApplicationStats(client.getApplicationStats(application.getName()));
			chargeBackAggregrateVO.setCloudApplication(application);
			chargeBackAggregrateVO.setSpaces(cloudSpaces);
			chargeBackAggregrateVOList.add(chargeBackAggregrateVO);
		}
		return chargebackService.getChargeBackUsage(chargeBackAggregrateVOList);
	}
	
	@RequestMapping(value = "/getSpaceList/{orgName:.+}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)	
	public List<String> getApplicationSpaceByOrg(@PathVariable String orgName ){
		
		CloudFoundryClient client = loginCloudFoundry();
		client.login();
		return client.getSpaces().stream().
				filter(cloudspace -> cloudspace.getOrganization().getName().equals(orgName)).map(cloudspace -> cloudspace.getName()).collect(Collectors.toList());
	}
	
	
	@RequestMapping(value = "/getOrgList", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)	
	public List<String> getOrgList(){
		CloudFoundryClient client = loginCloudFoundry();
		client.login();
		return client.getOrganizations().stream().map(org -> org.getName()).collect(Collectors.toList());
	}
	
	private URL getTargetURL(String target) {
		try {
			return URI.create(target).toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException("The target URL is not valid: " + e.getMessage());
		}
	}
	
	private CloudFoundryClient loginCloudFoundry() {
		CloudCredentials credentials = new CloudCredentials("amit.bansal@capgemini.com", "trtr22");
		CloudFoundryClient client = new CloudFoundryClient(credentials, getTargetURL("https://api.run.pivotal.io"));
		client.login();
		
		return client;
	}
	
	@RequestMapping(value = "/getapps", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public List<CloudApplication> getapps() {

		CloudFoundryClient client = loginCloudFoundry();
		return client.getApplications();

	}
	
	
	/**
	 * Store the Data into database
	 * @param usageList
	 */
	@RequestMapping(value = "/submit", method=RequestMethod.POST, consumes= MediaType.APPLICATION_JSON_VALUE)
	public void storeHistoricalRecords(@RequestBody List<Usage> usageList ){
		for(Usage usage : usageList){
			chargebackService.persistUsageData(usage);
		}
	}
	
	
	
}
