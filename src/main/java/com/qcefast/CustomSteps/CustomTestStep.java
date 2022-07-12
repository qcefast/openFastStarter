package com.qcefast.CustomSteps;

import com.qcefast.enums.Status;
import com.qcefast.exceptions.FastException;
import com.qcefast.fastXml.TestStep;
import com.qcefast.frameworkSpecific.FastDriver;
import com.qcefast.frameworkSpecific.FastElement;
import com.qcefast.util.FastUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.qcefast.constants.CustomActions;

public class CustomTestStep extends TestStep {

	public CustomTestStep() {}
	
	public CustomTestStep(TestStep tStep) {
		super(tStep);
	}
	
	@Override
	public void execute() throws FastException {
		//super.execute();
		CustomActions customAction = CustomActions.valueOf(getAction());
		FastDriver fastDriver = getFastRunProperties().getFastDriver();

		FastElement fastElement = null;
		WebDriver webDriver = fastDriver.getWebDriver();
		WebElement element = null;
		//Examples of how to develop custom actions
		switch (customAction) {
			case CUSTOM_ENTER_TEXT:
				fastElement = new FastElement(fastDriver);
				//This line mimicks how the Fast framework sets properties to find the object
				fastElement.setFastElementProperties(FastUtil.getProperties(getObject()));

				fastElement.findMe();
				fastElement.enterText(getData());
				setStatus(Status.PASSED);
				break;
			case CUSTOM_ENTER_TEXT_SELENIUM:
				element = webDriver.findElement(By.id("{enter_id}"));
				element.sendKeys(getData());
				setStatus(Status.PASSED);
				break;
			default:
				break;
			}
	}
}
