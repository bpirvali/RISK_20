package com.intuit.psd.risk.processor;

import com.intuit.psd.risk.interfaces.BusinessObjectModel;

public interface RuleProcessor {

	public void processRules(BusinessObjectModel bom) throws RiskException;
	
}