package com.intuit.psd.risk.processor.card;import java.sql.SQLIntegrityConstraintViolationException;import java.util.Date;import java.util.List;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import com.intuit.psd.risk.interfaces.BusinessObjectModel;import com.intuit.psd.risk.interfaces.StatusDAO;import com.intuit.psd.risk.processor.dao.AbstractStatusDAO;import com.intuit.psd.risk.processor.exceptions.RiskException;/** *  * @author asookazian *  *  */public class CardBatchStatusDAOImpl extends AbstractStatusDAO implements StatusDAO {	private static final Logger logger = LoggerFactory.getLogger(CardBatchStatusDAOImpl.class);    private final static int eid = 1;    private final static int successStatus = 2;    private final static int failureStatus = 3;    private final static String unknownCaseType = "[UNKNOWN]";    public static void main(String[] args) throws Exception {		logger.info("begin BatchCardStatusDAO.main()");		CardBatchStatusDAOImpl batchCardStatusDAO = new CardBatchStatusDAOImpl();		BusinessObjectModel bom = null;		RiskException riskException = new RiskException();		bom = new CardBatchBOMImpl();		batchCardStatusDAO.markStatusFailed(bom, riskException,1);    }    /**     * This method is called during STORM bolt execution. Purpose of this method     * is to process the merchant insert a record into the merchantalerts table     * and to update the Incoming Risk Events table.     *      * @param bom     *      * @return     */    public void markStatusSuccessfullyCompleted(BusinessObjectModel bom, int workerID) throws RiskException {		logger.info("begin BatchCardStatusDAO.markStatusSuccessfullyCompleted()");		CardBatchBOMImpl cardBatchBOM = null;				if(bom != null) {			cardBatchBOM = (CardBatchBOMImpl) bom;		}		else {			logger.error("BOM is null!");			throw new RiskException("BOM is null!");		}				List<ViolationAlert> violationAlerts = cardBatchBOM.getViolationAlerts();			for (ViolationAlert violationAlert : violationAlerts) {			//TODO: arbi/behzad:  this is not atomic and needs a voltdb sproc to be atomic			try {				getJdbcTemplate().update(AbstractStatusDAO.getUpdateIncomingRiskEventsSuccessQuery(), 									new Object[] {									successStatus,									 new java.util.Date(),																		cardBatchBOM.getAccountNumber()});								getJdbcTemplate().update(AbstractStatusDAO.getInsertMerchantAlertsQuery(),					                        new Object[] {					                        eid,					                        violationAlert.getMerchantAccountNumber(),											violationAlert.getAlertDate(),											violationAlert.getBatchCycleDate(),										//	generatePartitionKey(bom, workerID),											violationAlert.getCaseType() == null ? unknownCaseType : violationAlert.getCaseType(),					                        violationAlert.getAlertType(),					                        violationAlert.getViolationLevel(),					                        violationAlert.getViolatedRuleName(),					                        violationAlert.getRuleVersionId(),					                        violationAlert.getViolatedRuleDescription(),					                        violationAlert.getViolatedRuleActualValue(),					                        violationAlert.getViolatedRuleSet(),					                        violationAlert.getWorkQueueCategory(),					                        violationAlert.getAlertStatus()});			} 						catch(Exception e) {				Throwable t = e.getCause();				if (t instanceof SQLIntegrityConstraintViolationException) {					StringBuilder s = new StringBuilder();					s.append("Ignoring duplicate alerts exception for merchant:");					s.append(violationAlert.getMerchantAccountNumber());					s.append(", alert_timestamp:");					s.append(violationAlert.getAlertDate());					s.append("...");					logger.warn(s.toString());					//System.out.println(s.toString());				} else {					logger.error("Error occurred during update for jdbcTemplate: "+e.getMessage());					throw new RiskException("Error occurred during update for jdbcTemplate: "+e.getMessage());				}			}		}		    		logger.info("finished BatchCardStatusDAO.markStatusSuccessfullyCompleted()");    }    /**     * This method is called during STORM bolt execution in case an exception is     * caught. Purpose of this method is to update Incoming Risk Events table by setting     * processing_status = 3 which indicates that an error occurred during     * execution. Also, the retry value is incremented by one. The bolt execute     * may be re-executed n times after failure as per configuration setting.     *      * @param bom     *      * @param riskException     *      * @return     */       public boolean markStatusFailed(BusinessObjectModel bom, Throwable error, int workerID) throws RiskException {		logger.info("begin BatchCardStatusDAO.markStatusFailed()");			boolean successFlag = false;			CardBatchBOMImpl cardBatchBOM = null;		int status; 				if (bom != null) {			cardBatchBOM = (CardBatchBOMImpl)bom;		}		else {			logger.error("BOM is null!");			throw new RiskException("BOM is null!");		}				try {			logger.info("Just before the query where we get the exception");			logger.info("now updating Incoming Risk Events; processing_status != 2");			logger.info("Merchant account number"+ cardBatchBOM.getAccountNumber());			logger.info("Worker ID"+ workerID);		//	logger.info("partition key"+ generatePartitionKey(bom, workerID));			status = getJdbcTemplate().queryForInt(AbstractStatusDAO.getCheckForSuccessQuery(), new Object[] {cardBatchBOM.getAccountNumber()});			logger.info("Rows for the merchant account number   "+ status);			if (status == successStatus)				successFlag = true;		}		catch (Exception e) {			logger.error("Error occurred during queryForInt(): ", e);		}			if (!successFlag) {			logger.info("now updating Incoming Risk Events; processing_status != 2");			logger.info("Merchant account number"+ cardBatchBOM.getAccountNumber());	//		logger.info("partition key"+ generatePartitionKey(bom, workerID));			String errorMessage = "";	    	try {	    		errorMessage = (error.getCause()==null ? error.getMessage() : error.getCause().getMessage()) ;	    	}	    	catch(Exception e) {	    		logger.error("Exception occurred in getUpdateIncomingRiskEventsFailureQuery: ", e);	    	}			try {				getJdbcTemplate().update(AbstractStatusDAO.getUpdateIncomingRiskEventsFailureQuery(bom), 											new Object[] {											 failureStatus,											 new java.util.Date(),											 errorMessage,											cardBatchBOM.getAccountNumber()});			}			catch(Exception e){				logger.error("Error occurred during getUpdateIncomingRiskEventsFailureQuery(): ",e);			}		}			 		logger.info("finished BatchCardStatusDAO.markStatusFailed()");		return successFlag;    }    	@Override	public void insertIntoIncomingRiskEvents(String merchantAccountNumber , Date batchcycledate) throws RiskException {		logger.info("begin BatchCardStatusDAO.insertIntoIncomingRiskEvents()");			try {			logger.info("Merchant account number"+ merchantAccountNumber);			logger.info("Batch Cycle Date"+ batchcycledate);			getJdbcTemplate().update(AbstractStatusDAO.getInsertIncomingRiskEventsQuery(),new Object[] {merchantAccountNumber,batchcycledate,new java.util.Date()});			}		catch (Exception e) {			logger.error("Error occurred during insertIntoIncomingRiskEvents(): ", e);		}			}	@Override	public void cleanUpIncomingRiskEvents() throws RiskException {		logger.info("begin BatchCardStatusDAO.cleanUpIncomingRiskEvents()");			try {			getJdbcTemplate().update(AbstractStatusDAO.getIncomingRiskEventsCleanupQuery());			}		catch (Exception e) {			logger.error("Error occurred during cleanUpIncomingRiskEvents(): ", e);		}			}	@Override	public void cleanUpMerchantAlerts()	throws RiskException {		logger.info("begin BatchCardStatusDAO.cleanUpMerchantAlerts()");			try {			getJdbcTemplate().update(AbstractStatusDAO.getMerchantAlertsCleanupQuery());			}		catch (Exception e) {			logger.error("Error occurred during cleanUpMerchantAlerts(): ", e);		}			}}