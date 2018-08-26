package au.blindmot.eventhandler;



import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.RowSet;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MAttribute;
import org.compiere.model.MAttributeInstance;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MProductionLine;
import org.compiere.model.MQualityTest;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_OrderLine;
import org.compiere.model.X_M_ProductionLine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

import au.blindmot.model.MBLDMtomItemLine;
import au.blindmot.model.MBLDMtomProduction;
import au.blindmot.model.X_BLD_Line_ProductSetInstance;
import au.blindmot.utils.MtmUtils;

public class MBLDEventHandler extends AbstractEventHandler {

	private CLogger log = CLogger.getCLogger(MBLDEventHandler.class);
	private X_M_ProductionLine mProductionLine = null;
	private X_C_OrderLine orderLine = null;
	private MBLDMtomItemLine mToMProductionParent = null;
	private boolean DocVoidReverse = false; 
	
	@Override
	protected void initialize() {
		//register EventTopics and TableNames   
				registerTableEvent(IEventTopics.PO_AFTER_NEW, MProductionLine.Table_Name); 
				registerTableEvent(IEventTopics.PO_POST_UPADTE, MProductionLine.Table_Name); 
				registerTableEvent(IEventTopics.PO_AFTER_CHANGE, MProductionLine.Table_Name);
				registerTableEvent(IEventTopics.DOC_BEFORE_REVERSECORRECT, MBLDMtomProduction.Table_Name);
				registerTableEvent(IEventTopics.DOC_BEFORE_REVERSEACCRUAL, MBLDMtomProduction.Table_Name);
				registerTableEvent(IEventTopics.PO_BEFORE_NEW, MOrderLine.Table_Name);
				registerTableEvent(IEventTopics.PO_AFTER_NEW, MOrderLine.Table_Name);//PO to copy MAttributeSetInstance to
				log.info("----------<MBLDEventHandler> .. IS NOW INITIALIZED");
				}
	
	@Override
	protected void doHandleEvent(Event event) {
	
	PO po = getPO(event);
	
	if(po instanceof MProductionLine)
		{
			mProductionLine = (X_M_ProductionLine)po;
			log.warning("---------MProductionLine event triggered");
			log.warning("---------event: " + event);
			log.warning("---------PO: " + po.toString());
			
			handleMProductionLineEvent(po);//Handle all events with the same method
		}
	
	if(po instanceof MBLDMtomProduction)//The parent production is being voided or reversed.
	{
		DocVoidReverse = true;	
	}
	
	if(po instanceof MOrderLine)
	{
		log.warning("---------MOrderLine event triggered");
		log.warning("---------event: " + event);
		String trxName = po.get_TrxName();
		System.out.println(Env.getCtx().toString());
		orderLine = (MOrderLine)po;//The new OrderLine to copy the attribute instances to.
		log.warning("---------Line 83");
		log.warning("---------orderLine.getM_AttributeSetInstance_ID(): " + orderLine.getM_AttributeSetInstance_ID());
		
		if(event.getTopic().equalsIgnoreCase(IEventTopics.PO_AFTER_NEW))
		{
			//Check if there's a BLD Line ProductSetInstance; if none, create and set.
			int mProductID = orderLine.getM_Product_ID();
			MProduct currOrderLineProduct;
			boolean isMadeToMeasure = false;
			if (mProductID > 0) 
				{
					currOrderLineProduct = new MProduct(Env.getCtx(), mProductID , trxName);
					isMadeToMeasure = currOrderLineProduct .get_ValueAsBoolean("ismadetomeasure");
				}
			if(orderLine.get_Value("bld_line_productsetinstance_id") == null && isMadeToMeasure)//User didn't set bLDLineProductSetInstanceID 
				
				{
					X_BLD_Line_ProductSetInstance xBLDProdSetIns = new X_BLD_Line_ProductSetInstance(Env.getCtx(), 0, trxName);
					xBLDProdSetIns.saveEx(trxName);
					orderLine.set_ValueNoCheck("bld_line_productsetinstance_id", xBLDProdSetIns.get_ID());
					orderLine.save(trxName);
				}
			
			
			log.warning("---------orderLine.getM_AttributeSetInstance_ID(): " + orderLine.getM_AttributeSetInstance_ID());
			
			if(orderLine.get_Value("reference_id") == null)//It's a new record.
				{
					orderLine.set_ValueNoCheck("reference_id", orderLine.get_ID());
					orderLine.saveEx(trxName);
				}
			
			int refID = orderLine.get_ValueAsInt("reference_id");
			int orderLineID = orderLine.get_ID();
			if(orderLine.getM_AttributeSetInstance_ID() > 0 && refID == orderLineID) return;//Everything is OK.
			
			log.warning("---------Line 93");
			MProduct mProduct = new MProduct(Env.getCtx(), orderLine.getM_Product_ID(), trxName);
			if(mProduct.get_ValueAsBoolean("ismadetomeasure"))
				{
				MOrderLine copyFromOrderLine = copyAttributeInstance(orderLine, mProduct);
				if(copyFromOrderLine != null)//Then it's a new record, not a copied record.
					{
						
					System.out.println(copyFromOrderLine.get_Value("mtm_attribute"));
					orderLine.set_ValueOfColumn("mtm_attribute", copyFromOrderLine.get_Value("mtm_attribute"));
					orderLine.saveEx();
					}
					else
					{
						//Set qty field 
						int mAttributeInstanceID = orderLine.getM_AttributeSetInstance_ID();
						System.out.println("orderLine M_AttributeSetInstance_ID: " + mAttributeInstanceID);
						if(mAttributeInstanceID > 0)
							{
							
							BigDecimal l_by_w = MtmUtils.hasLengthAndWidth((int)mAttributeInstanceID).setScale(2, BigDecimal.ROUND_HALF_EVEN);
							if(l_by_w != Env.ZERO.setScale(2))
								{
									orderLine.setQtyEntered(l_by_w);	
								}
							
							if(l_by_w == Env.ZERO.setScale(2))//Check if it has length only
								{
									BigDecimal length = MtmUtils.hasLength((int)mAttributeInstanceID).setScale(2, BigDecimal.ROUND_HALF_EVEN);
									if(length != Env.ZERO.setScale(2))
										{
											orderLine.setQtyEntered(length);
										}
									}
								}
						}
				}
			
			
			orderLine.set_ValueOfColumn("reference_id", orderLine.get_ID());
			log.warning("-set_ValueOfColumn---line 127-----orderLine.get_ID: " + orderLine.get_ID());
			orderLine.save(trxName);
			return;
		}
		
	}
	
}

	
	/**
	 * Listens for and overrides the MProductionLine.beforeSave() method which n
	 * sets the MBLDMtomItemLine child productionlines to 0 for the end product.
	 * @param pobj
	 */
	private void handleMProductionLineEvent(PO pobj) {
		
		log.warning("---------- In handleMProductionLineEvent");
		String isEndProduct = "N";
		BigDecimal movementQty = BigDecimal.ZERO;
		
		log.warning("---------getmBLDMtomItemLineID() = " + getmBLDMtomItemLineID());
		
		if(!(getmBLDMtomItemLineID() > 0))//It's not an MTMProduction
		{
			log.warning("---------Not an MTM product");
			return;
		}
		
		if (getmBLDMtomItemLineID() > 0)//What does this actually test for?
		{
			log.warning("getmBLDMtomItemLineID() is > 0.");
			mToMProductionParent = new MBLDMtomItemLine(pobj.getCtx(), getmBLDMtomItemLineID(), pobj.get_TrxName());
		}
			
		log.warning("~Line 177 ");
		//Lines below left in for future debugging convenience, commented out for production.
		//system.out.println(mProductionLine.getM_Product_ID() && mToMProductionParent.getProductionQty().signum() == mProductionLine.getMovementQty().signum());
		log.warning("mToMProductionParent.getM_Product_ID(): " + mToMProductionParent.getM_Product_ID());
		log.warning("mProductionLine.getM_Product_ID(): " + mProductionLine.getM_Product_ID());
		log.warning("mToMProductionParent.getProductionQty().signum(): " + mToMProductionParent.getProductionQty().signum());
		log.warning("mProductionLine.getMovementQty().signum()" + mProductionLine.getMovementQty().signum());
		//
		if (getmBLDMtomItemLineID() > 0) 
		{
			int parentProductId = mToMProductionParent.getM_Product_ID();
			int mProductionLineProductID = mProductionLine.getM_Product_ID();
			log.warning("parentProductId = " + parentProductId + " mProductionLineProductID = " + mProductionLineProductID);
			
			
			if (parentProductId == mProductionLineProductID)//It's an end product
			{
				isEndProduct = "Y";
				log.warning("Is end product: " + isEndProduct);
				movementQty = BigDecimal.ONE;
				System.out.println("----------movementQty rnd 1: " + movementQty);
				if(DocVoidReverse)
					{
						movementQty = movementQty.negate();
						System.out.println("----------movementQty rnd 2: " + movementQty);
					}
			}
			else isEndProduct = "N";
		} 
		
		log.warning("Line 207: Is end product: " + isEndProduct);
		
		if ( (isEndProduct == "Y") && mProductionLine.getM_AttributeSetInstance_ID() != 0 )
		{
			String where = "M_QualityTest_ID IN (SELECT M_QualityTest_ID " +
			"FROM M_Product_QualityTest WHERE M_Product_ID=?) " +
			"AND M_QualityTest_ID NOT IN (SELECT M_QualityTest_ID " +
			"FROM M_QualityTestResult WHERE M_AttributeSetInstance_ID=?)";

			List<MQualityTest> tests = new Query(Env.getCtx(), MQualityTest.Table_Name, where, pobj.get_TrxName())
			.setOnlyActiveRecords(true).setParameters(mProductionLine.getM_Product_ID(), mProductionLine.getM_AttributeSetInstance_ID()).list();
			// create quality control results
			for (MQualityTest test : tests)
			{
				test.createResult(mProductionLine.getM_AttributeSetInstance_ID());
			}
		}
		
		log.warning("Line 220: Is end product: " + isEndProduct);
		
		if(isEndProduct == "Y")
		{
			mToMProductionParent = new MBLDMtomItemLine(pobj.getCtx(), getmBLDMtomItemLineID(), pobj.get_TrxName());
			StringBuilder sql = new StringBuilder("UPDATE ");
			sql.append("m_productionline SET ");
			sql.append("isendproduct = '" + isEndProduct + "'");
			sql.append(", movementqty = '" + movementQty + "'");
			sql.append(" WHERE m_productionline.bld_mtom_item_line_id = ");
			sql.append(getmBLDMtomItemLineID());
			sql.append(" AND m_product_id = ");
			sql.append(mProductionLine.getM_Product_ID());
			System.out.println("Attempting to DB.executeUpdate. Success is greater than 0 or Yes: " + (DB.executeUpdate(sql.toString(), pobj.get_TrxName())>0));
			
		}
		log.warning("At end of handleMProductionLineEvent(PO pobj)");
	}
	
	private int getmBLDMtomItemLineID() {
		int mBLDMtomItemLineID = mProductionLine.get_ValueAsInt("bld_mtom_item_line_id");
		log.warning("---------In getmBLDMtomItemLineID(). mBLDMtomItemLineID = " + mBLDMtomItemLineID);
		return mBLDMtomItemLineID;
	}
	
	private MOrderLine copyAttributeInstance(X_C_OrderLine toOrderLine, MProduct mProduct) {
		
		log.warning("---------In MBLDEventHandler.copyAttributeInstance()");
		X_C_OrderLine fromOrderLine = null;
		int fromOrderLineID = 0;
		String trxName = toOrderLine.get_TrxName();
		int toMproductID = mProduct.getM_Product_ID();
		log.warning("---------Line 224 toMproductID = " + toMproductID);
		
			fromOrderLineID = toOrderLine.get_ValueAsInt("reference_id");
			log.warning("---------Line 227 fromOrderLineID = " + fromOrderLineID);
		   if(fromOrderLineID != 0)
			{
			   log.warning("--------- line 230 fromOrderLineID does not equal 0");
			   fromOrderLine = new MOrderLine(Env.getCtx(),fromOrderLineID, trxName);
				int fromMProductID = fromOrderLine.getM_Product_ID();
				if(fromMProductID != toMproductID)
				{
					log.warning("--------- line 235...fromMProductID != toMproductID. fromMProductID:" + fromMProductID + "toMproductID: " + toMproductID);
					fromOrderLine = null;
					return (MOrderLine)fromOrderLine;//Wrong product, don't copy MAI.
				}
				log.warning("--------- line 239");
		MAttributeSetInstance fromMAttributeSetInstance = MAttributeSetInstance.get(Env.getCtx(), fromOrderLine.getM_AttributeSetInstance_ID(), mProduct.getM_Product_ID());
		MAttributeSetInstance toMAttributeSetInstance = new MAttributeSetInstance(Env.getCtx(),0,toOrderLine.get_TrxName());
		toMAttributeSetInstance.save(toOrderLine.get_TrxName());
		
		int fromAttributeSetId = fromMAttributeSetInstance.getM_AttributeSet_ID();
		
		int toAttributeSetInstanceId = toMAttributeSetInstance.getM_AttributeSetInstance_ID();
		toMAttributeSetInstance.setM_AttributeSet_ID(fromAttributeSetId);
		toMAttributeSetInstance.saveEx();
		
		RowSet fromAttributeInstances = getmAttributeInstances(fromMAttributeSetInstance.getM_AttributeSetInstance_ID());
		
		
	    try {
			while (fromAttributeInstances.next()) {  
				
			    int m_attribute_id = fromAttributeInstances.getInt(1);
			    String value = fromAttributeInstances.getString(2);
			    int mAttributeValueID = fromAttributeInstances.getInt(3);
			    
			    MAttribute mAttribute = new MAttribute(Env.getCtx(), m_attribute_id, toOrderLine.get_TrxName());
			    String attributeType = mAttribute.getAttributeValueType();
			    
			    if(attributeType.equalsIgnoreCase("N"))
			    {
			    	log.warning("--------- line 265");
			    	//Constructor for numeric attributes
			    	MAttributeInstance toMAttributeInstance = new MAttributeInstance(Env.getCtx(), m_attribute_id, 
			    	toAttributeSetInstanceId, new BigDecimal(value).setScale(1), toOrderLine.get_TrxName());
			    	toMAttributeInstance.setM_AttributeValue_ID(mAttributeValueID);
			    	//toMAttributeInstance.setValueNumber(new BigDecimal(value));
			    	toMAttributeInstance.saveEx();
			    }
			    else	
			    {	
			    	log.warning("--------- line 275");
			    	//Constructor for String attributes
				    MAttributeInstance toMAttributeInstance = new MAttributeInstance(Env.getCtx(),
				    m_attribute_id, toAttributeSetInstanceId, value, toOrderLine.get_TrxName());
				    toMAttributeInstance.setM_AttributeValue_ID(mAttributeValueID);
				    toMAttributeInstance.saveEx();
			    }
   }
		} catch (SQLException e) {
			log.severe("Could not get values from attributeinstance RowSet " + e.getMessage());
			e.printStackTrace();
		}  
	    
	    File tempFile = new File("/tmp/ignoreNewMAttributeInstance");
	    tempFile.delete();
	    log.warning("--------- line 290");
	    toOrderLine.setM_AttributeSetInstance_ID(toAttributeSetInstanceId);
	    toOrderLine.saveEx();
	    toMAttributeSetInstance.setDescription();
		toMAttributeSetInstance.saveEx();
		}
		   log.warning("--------- line 296");
		   if(fromOrderLine != null)fromOrderLine.saveEx();
		   return (MOrderLine)fromOrderLine;
	}
	
	private RowSet getmAttributeInstances(int mAttributeSetinstanceID)
	{
		StringBuilder sql = new StringBuilder("SELECT m_attribute_id, value, m_attributevalue_id ");
		sql.append("FROM m_attributeinstance mai ");
		sql.append(" WHERE mai.m_attributesetinstance_id = ");
		sql.append(mAttributeSetinstanceID);
		
		RowSet rowset = DB.getRowSet(sql.toString());
		return rowset;
	}
	
}
		