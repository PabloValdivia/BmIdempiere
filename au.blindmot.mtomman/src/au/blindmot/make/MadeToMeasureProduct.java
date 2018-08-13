package au.blindmot.make;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.model.MAttribute;
import org.compiere.model.MAttributeInstance;
import org.compiere.model.MAttributeSet;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.util.Env;

import au.blindmot.model.MBLDMtomCuts;
import au.blindmot.model.MBLDMtomItemLine;
import au.blindmot.utils.MtmUtils;

/**
 * @author phil
 *
 */
public abstract class MadeToMeasureProduct {

protected int wide = 0;
protected int high = 0;
protected int deep = 0;
protected int m_product_id = 0;
protected int mtom_item_line_id = 0;
protected String mtmInstanceParts = null;
protected CLogger log;
protected MBLDMtomItemLine mBLDMtomItemLine = null;
protected String trxName;



/**
 * 
 */
	public MadeToMeasureProduct() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * Default constructor
	 * @param product_id
	 * @param bld_mtom_item_line_id
	 * @param transactionName
	 */
	public MadeToMeasureProduct(int product_id, int bld_mtom_item_line_id, String transactionName) {
		
		m_product_id = product_id;
		mtom_item_line_id = bld_mtom_item_line_id;
		mBLDMtomItemLine = new MBLDMtomItemLine(Env.getCtx(), bld_mtom_item_line_id, transactionName);
		log = CLogger.getCLogger (getClass());
		trxName = transactionName;
	}
	
	/**
	 * 
	 * @param width
	 * @param height
	 * @param depth
	 * @param product_id
	 * @param bld_mtom_item_line_id
	 */
	public MadeToMeasureProduct(int width, int height, int depth, int product_id, int bld_mtom_item_line_id) {
		wide = width;
		high = height;
		deep = depth;
		m_product_id = product_id;
		mtom_item_line_id = bld_mtom_item_line_id;
		
	}
	
	/**
	 * 
	 * @return
	 */
	public abstract boolean getCuts();//Return true if successful, delete created records if fail.
	public abstract boolean createBomDerived();//Return true if successful, delete created records if fail.
	public abstract boolean deleteBomDerived();
	public abstract boolean deleteCuts(); 
	

	public int getWide() {
		return wide;
	}

	public void setWide(int wide) {
		this.wide = wide;
	}

	public int getHigh() {
		return high;
	}

	public void setHigh(int high) {
		this.high = high;
	}

	public int getDeep() {
		return deep;
	}

	public void setDeep(int deep) {
		this.deep = deep;
	}

	/**
	 * 
	 * @param mAttribute
	 */
	public abstract void interpretMattributeSetInstance();
	
	protected void addBldMtomCuts(int mProductID, int width, int length, int height){
		BigDecimal bigWidth = new BigDecimal(width);
		BigDecimal bigLength = new BigDecimal(length);
		BigDecimal bigHeight = new BigDecimal(height);
		if(mProductID != 0)
		{
			MBLDMtomCuts cut = new MBLDMtomCuts(Env.getCtx(), 0, trxName);
			cut.setWidth(bigWidth);
			cut.setLength(bigLength);
			cut.setHeight(bigHeight);
			cut.setM_Product_ID(mProductID);
			cut.setbld_mtom_item_line_ID(mtom_item_line_id);
			cut.saveEx();
		}
		
	}
	
	public AttributePair[] getMAttributeSetInstance() {
		int mAttributeSetInstance_ID = mBLDMtomItemLine.getattributesetinstance_id();
		MAttributeSetInstance mAttributeSetInstance = new MAttributeSetInstance(null, mAttributeSetInstance_ID, null);
		
		MAttributeSet mAttributeSet = new MAttributeSet(null, mAttributeSetInstance.getM_AttributeSet_ID(), null);
		
		/*Notes: mai contains the actual instance value, like '1600' or 'Right hand chain'
		 * attributes[] holds the names of the attributes in order that they appear in the Attribute Set Instance dialog box.
		 * Original plan was to 'interpret' parts required from the MAI then match them to BOM items. This is a bad idea.
		 * BETTER would be to select the parts directly from BOM via the MtmButton.
		 *HACK: Add to MProducts something like 'blind control', 'blind bracket', 'blind tube', 'blind bottom bar' etc.
		 * 
		 * So what do we set with MAIs? Things that aren't directly related to parts!
		 * Width, drop, control side, location - what else?
		 * But what happens when the tube size changes as blinds get bigger, ie going from a 38mm to 44mm tube? 
		 * The mech parts change. So the blind controls will have to stay as MAIs. As will most other parts.
		 */
		
		MAttribute[] attributes = mAttributeSet.getMAttributes(true);
		List<AttributePair> list = new ArrayList<AttributePair>();
		for (int i = 0; i < attributes.length; i++) {
			MAttributeInstance mai = attributes[i].getMAttributeInstance(mAttributeSetInstance.getM_AttributeSetInstance_ID());
			if (mai == null || mai.getValue() == null || attributes[i] == null)
			{
				break;
			}
			else
			{
				
				if(attributes[i].getName() == null)
				{
					log.log(Level.SEVERE, "Atrributes missing from mtom_item_line_id: " + mtom_item_line_id);
				}
				
				else if(attributes[i].getName().equalsIgnoreCase("Width"))
				{
					wide = Integer.parseInt(mai.getValue());
				}
				else if(attributes[i].getName().equalsIgnoreCase("Drop"))
				{
					high = Integer.parseInt(mai.getValue());
				}
				else if(attributes[i].getName().equalsIgnoreCase("Height"))
				{
					high = Integer.parseInt(mai.getValue());
				}
				else if(attributes[i].getName().equalsIgnoreCase("Depth"))
				{
					deep = Integer.parseInt(mai.getValue());
				}
				
				else 
					{
						list.add(new AttributePair(attributes[i].getName().toString(), mai.getValue()));//Add the remaining attributes to an AtributePair

					}
				
				/*
				 * Notes: mai contains the actual instance value, like '1600' or 'Right hand chain'
				 * attributes[] holds the names of the attributes in order that appear in the Attribute Set Instance dialog box.
				 * 
				 */
			}
				
		}
		if(mBLDMtomItemLine.getinstance_string() != null)mtmInstanceParts = mBLDMtomItemLine.getinstance_string();
		return list.toArray(new AttributePair[list.size()]);
		/*mtmInstanceParts
		 * TODO: Add fields for fabric and chain get/set/handle MBLDMtomItemLine.instance_string
		 * Note MBLDMtomItemLine.instance_string holds the values for chain and fabric product_ids
		 */
		
		
	}
	
	public int getWaste(int mprodID) {
		StringBuilder sql = new StringBuilder("SELECT ma.value FROM m_attributeinstance ma");
		sql.append(" WHERE ma.m_attributesetinstance_id =");
		sql.append(" (SELECT mp.m_attributesetinstance_id FROM m_product mp WHERE mp.m_product_id = ");
		sql.append(mprodID + ")");
		sql.append(" AND ma.m_attribute_id =");
		sql.append(" (SELECT mat.m_attribute_id FROM m_attribute mat WHERE mat.name LIKE'%aste%')");
		int waste = 0;
		MtmUtils.attributePreCheck("Waste%");
		MtmUtils.attributePreCheck("Waste");
		MtmUtils.attributePreCheck("waste%");
		MtmUtils.attributePreCheck("waste");
		waste = DB.getSQLValue(trxName, sql.toString());
		return waste;
	}
	
	/**
	 * Possibly change to non abstract and write generic method body.
	 * Or create another method to provide the MAttributeSetInstance interpretation and return it in an array,
	 * then have an abstract method that requires subclasses to do something with it?
	 * Set object dimension fields so that other methods can use them.
	 * e.g. int theHeight = //some code;
	 * this.height = theHeight;
	 * Set other fields unique to concrete class
	 * e.g. int theLeftMech = //some code;
	 * this.leftMech = theLeftMech;//set the product_iD of the left mech
	 */ 

}
