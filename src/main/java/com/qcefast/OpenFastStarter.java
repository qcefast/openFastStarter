package com.qcefast;

import java.io.File;
import java.util.*;

import com.google.devtools.common.options.OptionsParser;
import com.qcefast.enums.Status;
import com.qcefast.exceptions.FastException;
import com.qcefast.fastXml.FastXml;
import com.qcefast.fastXml.FunctionalTestStep;
import com.qcefast.fastXml.TestScript;
import com.qcefast.fastXml.TestStep;
import com.qcefast.util.Fast;
import com.qcefast.util.FastArgs;
import com.qcefast.util.FastRunProperties;
import com.qcefast.util.FastUtil;
import com.qcefast.CustomSteps.CustomTestStep;

public class OpenFastStarter {

	/**
	 * The entry point of application.
	 * Example input:
	 * <br>
	 * --fastRunConfig properties/fast-runconfig.properties --envRunConfig properties/env-runconfig.properties --testDriverRunConfig properties/test-driver-runconfig.properties
	 * <br>
	 * Usage: <br>
	 *   --callerIdentity (a string; default: ""): <br>
	 *     The directory of the caller-identity.json file the notificationService will use to sendStatuses <br>
	 *   --envRunConfig (a string; default: ""): <br>
	 *     The full path to a envRunConfig properties file <br>
	 *   --fastRunConfig (a string; default: ""): <br>
	 *     The full path to a fastRunConfig properties file <br>
	 *   --testDriverRunConfig (a string; default: ""): <br>
	 *     The full path to a testDriverRunConfig properites file <br>
	 * @param args the input arguments
	 */
	public static void main(String[] args) {

		System.out.println("ARGS: " + Arrays.toString(args));
		Set<Status> fastXmlStatuses = new HashSet<Status>();
		try {
			OptionsParser parser = OptionsParser.newOptionsParser(FastArgs.class);
	    	parser.parseAndExitUponError(args);
	    	FastArgs options = parser.getOptions(FastArgs.class);
			if (options.fastRunConfig.isEmpty() || options.envRunConfig.isEmpty() || options.testDriverRunConfig.isEmpty()) {
				printUsage(parser);
				return;
			}

	    	Fast fastHub = null;
			if (options.callerIdentityLocation == null || options.callerIdentityLocation.isEmpty()) {
				fastHub = new Fast(new File(options.fastRunConfig), new File(options.envRunConfig), new File(options.testDriverRunConfig));
			} else {
				fastHub = new Fast(options.callerIdentityLocation, new File(options.fastRunConfig), new File(options.envRunConfig), new File(options.testDriverRunConfig));
			}
	    	Set<String> fastActions = fastHub.getAllActions();
			List<FastXml> fastXmls = fastHub.getFastXmlDrivers();
			FastRunProperties runProperties = fastHub.getFastRunProperties();
			
			for (FastXml fastXml : fastXmls) { //Loop through each xmlDriver
				fastHub.setStartTime(fastXml); //This method sets the start time in fastXml, as well as the placeholder field tempStartTime in fastRunProperties
				fastXml.setFrameworkVersion(fastHub.getAppVersion());
				SCRIPT_LEVEL:
				for (TestScript tScript : fastXml.getTestScripts()) { //Cycle through each testScript in a fastXml
					tScript.setStartTime(fastHub.getCurrentDateTime());
					try {
						FSTEP_LEVEL: for (FunctionalTestStep fStep : tScript.getFunctionalTestSteps()) { //Cycle through each functionalStep in a TestScript
							for (TestStep tStep : fStep.getTestSteps()) { //Cycle through each testStep in a functionalStep
								try {
									tStep.setStartTime(fastHub.getCurrentDateTime());
									if (fastActions.contains(tStep.getAction())) { //Action is a fast action
										tStep.execute();
									} else { //Action is a custom action
										CustomTestStep cStep = new CustomTestStep(tStep);
										cStep.execute();
										tStep = cStep;
										tStep.setStatus(cStep.getStatus());
									}
									tStep.setEndTime(fastHub.getCurrentDateTime());
									fastHub.sendTestStepStatus(tStep, runProperties.getSendStatuses());
								} catch (Exception e) {
									tStep.setFastException(e);
									fastHub.writeException(tStep);
									switch (tStep.getFailType()) {
									case FAIL_TSTEP:
										tStep.setEndTime(fastHub.getCurrentDateTime());
										fastHub.sendTestStepStatus(tStep, runProperties.getSendStatuses());
										continue;
									case FAIL_FSTEP:
										tStep.setEndTime(fastHub.getCurrentDateTime());
										fStep.setEndTime(fastHub.getCurrentDateTime());
										fastHub.sendFunctionalStepStatus(fStep, runProperties.getSendStatuses());
										continue FSTEP_LEVEL;
									case FAIL_TCASE:
										tStep.setEndTime(fastHub.getCurrentDateTime());
										fStep.setEndTime(fastHub.getCurrentDateTime());
										tScript.setEndTime(fastHub.getCurrentDateTime());
										fastHub.sendTestScriptStatus(tScript, runProperties.getSendStatuses());
										continue SCRIPT_LEVEL;
									case FAIL_TSUITE:
										tStep.setEndTime(fastHub.getCurrentDateTime());
										fStep.setEndTime(fastHub.getCurrentDateTime());
										tScript.setEndTime(fastHub.getCurrentDateTime());
										fastHub.sendTestSuiteStatus(fastXml, runProperties.getSendStatuses());
										break SCRIPT_LEVEL;
									default:
										break;
									}
								}
							}
							fStep.setEndTime(fastHub.getCurrentDateTime());
							fStep.setStatus(fastHub.getWorstTestStepStatus(fStep.getTestSteps()));
							fastHub.sendFunctionalStepStatus(fStep, runProperties.getSendStatuses());
						}
						tScript.setEndTime(fastHub.getCurrentDateTime());
						tScript.setStatus(fastHub.getWorstFunctionalTestStepStatus(tScript.getFunctionalTestSteps()));
						fastHub.sendTestScriptStatus(tScript, runProperties.getSendStatuses());
					} finally {
						if (fastHub.getFastRunProperties().getCloseDriverAfterScript()) {
							fastHub.closeDrivers();
						}
					}
				}
				fastXml.setEndTime(fastHub.getCurrentDateTime());
//				fastXml.setStatus(fastHub.resolveTestSuiteStatus(fastXml.getTestScripts()));
				fastHub.writeHtmlReport(fastXml, fastHub.getFastRunProperties());
				fastHub.sendTestSuiteStatus(fastXml, runProperties.getSendStatuses());
				fastXmlStatuses.add(fastXml.getStatus());
			}
		} catch (FastException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		Status worstStatus = FastUtil.getWorstStatus(fastXmlStatuses);
		System.out.println("WORST STATUS: " + worstStatus.toString());
		if (worstStatus == Status.PASSED) {
			System.exit(0);
		} else {
			System.exit(1);
		}
	}

	/**
	 * This method is used to print out the proper usage for running Fast_Main.
	 * @param parser an instance of {@link OptionsParser}
	 */
	private static void printUsage(OptionsParser parser) {
		System.out.println("Usage: --fastRunConfig FastRunConfig/fast-runconfig.properties --envRunConfig EnvRunConfig/env-runconfig.properties --testDriverRunConfig TestDriverRunConfig/test-driver-runconfig.properties");
		System.out.println(parser.describeOptions(Collections.<String, String>emptyMap(),
				OptionsParser.HelpVerbosity.LONG));
		System.exit(0);
	}
}
