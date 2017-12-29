package au.blindmot.make;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.compiere.model.MProduct;
import org.compiere.model.MProductBOM;
import org.compiere.model.X_M_PartType;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;

import au.blindmot.model.MBLDBomDerived;
import au.blindmot.model.MBLDMtomCuts;
import au.blindmot.model.MBLDMtomItemLine;
import au.blindmot.utils.MtmUtils;


public class RollerBlind extends MadeToMeasureProduct {
	
	private ArrayList<KeyNamePair> controlType = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> bottomBar = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> TubularNCM = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> rollerBracket = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> chainSafe = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> chainAcc = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> rollerTube = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> endCap = new ArrayList<KeyNamePair>();
	private ArrayList<KeyNamePair> miscItem = new ArrayList<KeyNamePair>();
	private String blindControlIns = null;
	private String TNCMIns = null;
	private String rollerBracketIns = null;
	private String rollerBracketColIns = null;
	private String mechColIns = null;
	private String chainSafeIns = null;
	boolean isChainControl = false;
	
	//Various BOM parts to make the blind
	private int controlBracketID = 0;
	private int nonControlBracketID = 0;
	private int controlID = 0;
	private int nonControlID = 0;
	private int chainSafeID = 0;
	private int rollerTubeID = 0;
	private int fabricID = 0;
	private int endCapID = 0;
	private int bottomBarID = 0;
	
	//Cuts for cut to size items.
	private int rollerTubeCut = 0;
	private int bottomBarCut = 0;
	private int fabricWidth = 0;
	private int fabricDrop = 0;

	
	
	

	public RollerBlind (int product_id, int bld_mtom_item_line_id) {
		super(product_id, bld_mtom_item_line_id);
	}
	
	
	@Override
	public void interpretMattributeSetInstance() {
		AttributePair[] attributePair = getMAttributeSetInstance();
		
		String mInstance;
		String mInstanceValue;
		MBLDMtomItemLine mtmrolleritem = new MBLDMtomItemLine(Env.getCtx(), mtom_item_line_id, null);
		
		//Set fields based on the AttributePair[] contents.
		for(int i = 0; i < attributePair.length; i++)
		{
			mInstance = attributePair[i].getInstance();
			mInstanceValue = attributePair[i].getInstanceValue();
			
			if(mInstance.equalsIgnoreCase("Location"))
			{
				if(mtmrolleritem.getlocation() == null || mtmrolleritem.getlocation().length()<1)
				mtmrolleritem.setlocation(mInstanceValue);
			}
			else if(mInstance.equalsIgnoreCase("Blind control"))
			{	
				blindControlIns = mInstanceValue;
				if(mInstanceValue.equalsIgnoreCase("Chain control"))
				{
					isChainControl = true;
				}
				
			}	
			else if(mInstance.equalsIgnoreCase("Blind non control mech"))
			{
				TNCMIns = mInstanceValue;
			}
			else if(mInstance.equalsIgnoreCase("Roller bracket"))
			{
				rollerBracketIns = mInstanceValue;
			}
			else if(mInstance.equalsIgnoreCase("Mech colour"))
			{
				mechColIns = mInstanceValue;
			}
			else if(mInstance.equalsIgnoreCase("Bracket colour"))
			{
				rollerBracketColIns = mInstanceValue;
			}
			else if(mInstance.equalsIgnoreCase("Chain safe"))
			{
				chainSafeIns = mInstanceValue;
			}
		}
		mtmrolleritem.saveEx();
	}
	

	@Override
	public boolean getCuts() {
		//Get a value for field rollerTubeCut
		ArrayList <Integer> headRailComps = new ArrayList <Integer>();
		headRailComps.add(nonControlBracketID);
		headRailComps.add(controlBracketID);
		headRailComps.add(controlID);
		headRailComps.add(nonControlID);
		
		rollerTubeCut = wide - MBLDMtomCuts.getDeductions(headRailComps, MtmUtils.MTM_HEAD_RAIL_DEDUCTION);
		
		//Get a value for field fabricWidth & fabricDrop
		fabricWidth = rollerTubeCut - MBLDMtomCuts.getDeduction(fabricID, MtmUtils.MTM_FABRIC_DEDUCTION);
		fabricDrop = high + MBLDMtomCuts.getDeduction(fabricID, MtmUtils.MTM_FABRIC_ADDITION);
		bottomBarCut = fabricWidth - MBLDMtomCuts.getDeduction(bottomBarID, MtmUtils.MTM_BOTTOM_BAR_DEDUCTION);
		
		if(fabricID !=0 )addBldMtomCuts(fabricID, fabricWidth, fabricDrop, 0);
		if(rollerTubeID !=0 )addBldMtomCuts(rollerTubeID,0,rollerTubeCut,0);
		if(bottomBarID !=0 )addBldMtomCuts(bottomBarID,0,bottomBarCut ,0);
		
		return true;
		/*
		 * Possibly create utility method in MtmUtils to get deductions - pass an array of productIds to it?
		 * TODO: Check that KeyNamePair is the right thing to return?
		 * Perhaps a 2 dimensional array would be better? Would need to change here and in WindowFurnishing.java
		 */
	
	
		/*
		 *TODO: 1. Get head rail deductions
		 *2. tube cut length = width -  sum(rail head deductions)
		 *3. fabric cut width =  tube - fabric deduction
		 *4. fabric cut length = length + Fabric length addition - which is a non instance attribute of the
		 *the actual finished product, eg 'roller blind'.
		 *5. bottom bar cut length = fabric cut width - bottom bar deduction.
		 *6. As each cut is determined, the qty in the BOM derived items for fabric, tube and bottom bar 
		 *will need to be set.
		 */
		
	}

	@Override
	public boolean createBomDerived() {//This is the first method called when processing
		/*NOTE: IT SHOULD BE CALLED AFTER getCuts() SO THE CUT TO LENGTH BOMS CAN BE DETERMINED!!
		 * What we want to do here is add all the parts required to make the blind.
		 * The info we have is: 
		 * 1. the list of actual ProductIDs from the mtmInstanceParts field which is set when getMAttributeSetInstance() is called.
		 * 2. The attribute pairs from the getMAttributeSetInstance() method which returns an array of MAttributePairs which will need to matched to parts
		 * The following partypes need to be determined then records added to the bld_mtom_bomderived table:
		 * Chain (direct from mtmInstanceParts)
		 * Bottom bar (direct from mtmInstanceParts)
		 * Fabric(direct from mtmInstanceParts)
		 * Tubular blind control
		 * Roller bracket
		 * TNCN - tubular non control mech
		 * etc
		 * From the AttributePair[]
		 * 
		 * 
		 */
		
		interpretMattributeSetInstance();
		populatePartTypes(m_product_id);//Gets the ArrayLists of parts
		setValueProductID();//Interprets the attribute instances and picks parts from ArrayLists to match, also creates BOM lines for chain driven blinds.
		addMtmInstancePartsToBomDerived();//Creates BOM lines from the products in the 'instance_string' column.
		
		addBomDerivedLines(controlID, null);
		addBomDerivedLines(nonControlID, null);
		addBomDerivedLines(nonControlBracketID, null);
		addBomDerivedLines(controlBracketID, null);
		addBomDerivedLines(chainSafeID, null);
		addBomDerivedLines(rollerTubeID, null);
		addBomDerivedLines(endCapID, null);
		//TODO: Handle adding tube tape, bubble, packaging tape, masking tape, base bar stickers, tube selection
		//Tube selection: create static utility method in MtmUtils? Need to determine bending moment in tube centre.
		
		return true;
	}

	@Override
	public boolean deleteBomDerived() {
		// TODO Auto-generated method stub
		return true;
	}


	@Override
	public boolean deleteCuts() {
		MBLDMtomCuts[] cuts = null;
	if(mBLDMtomItemLine!=null)
	{
		cuts = mBLDMtomItemLine.getCutLines(Env.getCtx(), mtom_item_line_id);
	}
	for(int i =0; i<cuts.length; i++)
	{
		cuts[i].deleteEx(true);
	}

		return true;
	}
	
	/**
	 * Searches for all the BOM parts from product with m_product_id
	 * sorts them into ArrayList with a KeyNamePair array for each relevant
	 * m_parttype_id
	 * @param m_product_id
	 */
	private void populatePartTypes(int m_product_id) 
	{
		StringBuilder sql = new StringBuilder("SELECT mp.m_parttype_id, mp.m_product_id, mp.name ");
		sql.append("FROM m_product mp INNER JOIN m_product_bom mpb ");
		sql.append("ON mp.m_product_id = mpb.m_productbom_id ");
		sql.append("AND mpb.m_product_id = ");
		sql.append(m_product_id);
		sql.append(" ORDER BY mp.m_parttype_id");
		
		List<String> mpName = new ArrayList<String>();
		List<Integer> partIDs = new ArrayList<Integer>();
		List<Integer> prodIDs = new ArrayList<Integer>();
		
		ResultSet rs = DB.getRowSet(sql.toString());
		System.out.println(rs.toString());
		
		    try {
				while (rs.next()) {
					partIDs.add(rs.getInt(1));
					prodIDs.add(rs.getInt(2));
					mpName.add(rs.getString(3));
				}
			} catch (SQLException e) 
		    {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    finally
		    {
		    	DB.close(rs);
		    	rs = null;
		    }
		
		Integer parts[] = partIDs.toArray(new Integer[(partIDs.size())]);
		String names[] = mpName.toArray(new String[mpName.size()]);
		Integer productIDs[] = prodIDs.toArray(new Integer[(prodIDs.size())]);
		
		String controlComp = "Tubular blind control";
		String bBar = "Bottom bar";
		String TNCM = "TNCM";
		String rBracket = "Roller bracket";
		String cSafe = "Chain Safe";
		String cAcc = "Chain accessory";
		String rTube = "Roller tube";
		String eCap = "End Cap";
	
		
			for(int i = 0; i < parts.length; i++) {
	
				X_M_PartType mPartType = new X_M_PartType(null, parts[i], null);
				String partType = (String) mPartType.get_Value("name");
				
				if(partType == null)
				{
					miscItem.add(new KeyNamePair(productIDs[i], names[i]));;
				}
				else if(partType.equalsIgnoreCase(controlComp))
				{
					controlType.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(bBar))
				{
					bottomBar.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(TNCM))
				{
					TubularNCM.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(rBracket))
				{
					rollerBracket.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(cSafe))
				{
					chainSafe.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(cAcc))
				{
					chainAcc.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(rTube))
				{
					rollerTube.add(new KeyNamePair(productIDs[i], names[i]));
				}
				else if(partType.equalsIgnoreCase(eCap))
				{
					endCap.add(new KeyNamePair(productIDs[i], names[i]));
				}
				
			}
			
			
		}
	
	/*
	 * Some business logic to consider:
	 * Tube sizes vary in relation to finished size. This can hard coded then made a setting
	 * Each tube requires different mechs - the tube size for each mech will be in it's description field
	 * so it can easily found.
	 * Each bottom bar requires different end caps.
	 * Rollers with chains get 'chain accessory' part types.
	 */
		
		
		/*Get the type part that the 1st partid is.
		 * 
		 * Iterate through the 3 arrays while where still on the same partID
		 * Create a keynamePair from the name and productid.
		 * Append/make the appropriate ListBox items from the keynamepair
		 * when the partID changes, repeat from beginning.
		 * 
		 * 
		 * DB.getRowSet(sql) or getKeynamePair()?
		*Get BOM: SELECT mp.m_parttype_id, mp.m_product_id FROM m_product mp INNER JOIN m_product_bom mpb ON mp.m_product_id = mpb.m_productbom_id AND mpb.m_product_id = '1000010';
		*or SELECT mp.name, mp.m_product_id, mp.m_parttype_id FROM m_product mp INNER JOIN m_product_bom mpb ON mp.m_product_id = mpb.m_productbom_id AND mpb.m_product_id = '1000010';
		*Figure out which BOM items are: Chain, control, roller bracket, bottom bar, TNCM (tubular non control mech)
		*/
	
	
	private void setValueProductID() {
		
		/*Following need to be determined
		 * 
	private int nonControlBracketID = 0;
	private int controlID = 0;
	private int nonControlID = 0;
	private int chainSafeID = 0;
	private int rollerTubeID = 0;
	private int endCapID = 0;
	
		 */
		//Add the chain accessories
		if(isChainControl)
		{
			for (KeyNamePair ca : chainAcc) {
		        StringBuilder sql = new StringBuilder("SELECT m_product_bom_id ");
		        sql.append("FROM m_product_bom ");
		        sql.append("WHERE m_product_id = ");
		        sql.append(m_product_id);
		        sql.append("AND m_productbom_id = ");
		        sql.append(ca.getID());
		        
		        int m_product_bom_id = DB.getSQLValue(null, sql.toString());
				MProductBOM mProductBom = new MProductBOM(Env.getCtx(), m_product_bom_id, null);
				BigDecimal qty = mProductBom.getBOMQty();
				
				MBLDBomDerived mBomDerived = new MBLDBomDerived(Env.getCtx(), 0, null);
				mBomDerived.setbld_mtom_item_line_ID(mtom_item_line_id);
				mBomDerived.setM_Product_ID(ca.getKey());
				mBomDerived.setQty(qty);
				mBomDerived.saveEx();
				
		        }
		}
		
		
		//Resolve controlBracketID
		//TODO: Protocol must be bracket description contains: control(literal), colour(instance), type(instance) - dual, single extension etc.
		
			controlID = resolveComponents(controlType, blindControlIns);//Resolve controlID
			nonControlID = resolveComponents(TubularNCM, TNCMIns);//Resolve nonControlID
			//TODO: May need more logic to determine brackets as below 2 statement assign the same value.
			nonControlBracketID = resolveComponents(rollerBracket, rollerBracketColIns, rollerBracketIns);//Resolve nonControlBracketID
			controlBracketID = resolveComponents(rollerBracket, rollerBracketColIns, rollerBracketIns);//
			chainSafeID = resolveComponents(chainSafe, chainSafeIns);//Resolve chainSafeID
			
			
			/**
			 * Set roller tube ID based on blind width only
			 * 
			*/
			String tubeDuty = "";
			if(wide > 2500)tubeDuty = "HD";
			else {
				tubeDuty = "MD";
			}
			
			rollerTubeID = resolveComponents(rollerTube, tubeDuty);
			 
			 /* TODO: Resolve rollerTubeID via calculation
			 * There's a method MtmUtils.getBendingMoment(int length, int fabricProductId, int basebarProductId)
			 *that returns the bending moment (BM) in kg-metres.
			 *the tube to select will be the smallest tube cross section that has a rated BM that exceeds
			 *the computed BM for the blind being made.
			 *
			 * TODO: Handle blinds to be on the same size tube if they're all in the same room.
			 */
			//TODO: Resolve endCapID
	}
	/**
	 * Note: There's 2 columns in the m_product_bom_id table
	 * that both look like m_product_bom_id
	 * @param mProductBomid
	 * @return
	 */
	private BigDecimal getBomQty(int mProductBomid) { 
		 StringBuilder sql = new StringBuilder("SELECT m_product_bom_id ");
	        sql.append("FROM m_product_bom ");
	        sql.append("WHERE m_product_id = ");
	        sql.append(m_product_id);
	        sql.append("AND m_productbom_id = ");
	        sql.append(mProductBomid);
	        
	        int m_product_bom_id = DB.getSQLValue(null, sql.toString());
			MProductBOM mProductBom = new MProductBOM(Env.getCtx(), m_product_bom_id, null);
			BigDecimal qty = mProductBom.getBOMQty();
		
		return qty;
		
	}
	
	private void addMBLDBomDerived(int mProductId, BigDecimal qty, String description) {
		MBLDBomDerived mBomDerived = new MBLDBomDerived(Env.getCtx(), 0, null);
		mBomDerived.setbld_mtom_item_line_ID(mtom_item_line_id);
		mBomDerived.setM_Product_ID(mProductId);
		mBomDerived.setQty(qty);
		if(description != null)mBomDerived.setDescription(description);
		mBomDerived.saveEx();
	}
	
	private int resolveComponents(ArrayList<KeyNamePair> parts, String instanceParse) {
	
		int part_ID = 0;
		String partDescription = "";
		for(KeyNamePair partPair : parts) 
		{
			part_ID = partPair.getKey();
			MProduct productToExamine = new MProduct(Env.getCtx(), part_ID, null);
			partDescription = productToExamine.getDescription().toLowerCase();
			
			if(instanceParse!=null)
			{
				if(partDescription.contains(instanceParse.toLowerCase()))
				{
					break;//part_ID should now contain the right MProductID
				}
			}
		}
		if(part_ID == 0)//The part couldn't be found.
	{
		log.warning(instanceParse + partDescription + "---------------Could not resolve control from Attribute Instance.. Please ensure that products are set up with descriptions that match Attributes.");
	}
		return part_ID;
	}
	
	private int resolveComponents(ArrayList<KeyNamePair> parts, String instanceParse, String instanceParse2) {
		
		int part_ID = 0;
		
		for(KeyNamePair partPair : parts) 
		{
			part_ID = partPair.getKey();
			MProduct productToExamine = new MProduct(Env.getCtx(), part_ID, null);
			String partDescription = productToExamine.getDescription().toLowerCase();
			
		if(instanceParse != null && instanceParse2 != null)
		  {
			if(partDescription.contains(instanceParse.toLowerCase()) && partDescription.contains(instanceParse2.toLowerCase()))
			{
				break;//part_ID should now contain the right MProductID
			}
		  }
		}
		if(part_ID == 0)//The bracket couldn't be found.
	{
		log.warning(instanceParse + "---------------Could not resolve control from Attribute Instance.. Please ensure that products are set up with descriptions that match Attributes.");
	}
		return part_ID;
	}
	
	
	private void addMtmInstancePartsToBomDerived(){
	if(mtmInstanceParts != null)//Add the parts stored in the bldmtomlineiem instance_string column
	{
	
	String patternString = "[0-9][0-9][0-9][0-9][0-9][0-9][0-9]";
    Pattern pattern = Pattern.compile(patternString);
	
    if(mtmInstanceParts!=null && mtmInstanceParts.toString().contains("_"))
	{
		//Split the value of the mtm_attribute column, first value id fabric, second is chain, third is bottom bar
		String[] products = mtmInstanceParts.toString().split("_");
		
		for(int i = 0; i < products.length; i++)
		{
			Matcher matcher = pattern.matcher(products[i]);
			boolean matches = matcher.matches();
			if(matches)
			{
				int mProductId = Integer.parseInt(products[i]);
				BigDecimal qty = getBomQty(mProductId);
				addMBLDBomDerived(mProductId, qty, null);
				
				if(i == 0)fabricID = mProductId;
				if(i == 2)bottomBarID = mProductId;
			}
		}
	}
	}
}
	
	private void addBomDerivedLines(int partProduct_id, String description){
		if(partProduct_id != 0)
		{
			addMBLDBomDerived(partProduct_id, getBomQty(partProduct_id), description);
		}
		
	}
	
	private void addBldMtomCuts(int mProductID, int width, int length, int height){
		BigDecimal bigWidth = new BigDecimal(width);
		BigDecimal bigLength = new BigDecimal(length);
		BigDecimal bigHeight = new BigDecimal(height);
		if(mProductID != 0)
		{
			MBLDMtomCuts cut = new MBLDMtomCuts(Env.getCtx(), null, null);
			cut.setWidth(bigWidth);
			cut.setLength(bigLength);
			cut.setHeight(bigHeight);
			cut.setM_Product_ID(mProductID);
			cut.setbld_mtom_item_line_ID(mtom_item_line_id);
			cut.saveEx();
		}
		
	}
	

}
