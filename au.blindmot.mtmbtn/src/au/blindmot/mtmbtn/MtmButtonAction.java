/**
 * 
 */
package au.blindmot.mtmbtn;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.action.IAction;
import org.adempiere.webui.adwindow.ADWindow;
import org.adempiere.webui.adwindow.ADWindowContent;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListItem;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.GridTab;
import org.compiere.model.MOrderLine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Vbox;
import au.blindmot.model.MBLDMtomItemLine;

/**
 * @author phil
 * This should really be done by an extra field in the c_orderline table 'mtmoptions' that stores
 * the options and the product_ids like mattributeinstances eg 'chain=1000012_fabric=1000018'
 * and should be entered and validated by an editor.
 * NOTE: If the button is called from a mtmproduction window, any changes to the values are written to the
 * mbldtmtomlineitem. The instance variables are still read from the c_orderline.
 *
 */
public class MtmButtonAction implements IAction, EventListener<Event> {

	private static CLogger log = CLogger.getCLogger(MtmButtonAction.class);
	private AbstractADWindowContent panel;
	private ConfirmPanel 	confirmPanel = new ConfirmPanel(true);
	private GridTab 		tab = null;
	private Listbox fabFamily = new Listbox();
	private Listbox fabColour = new Listbox();
	private Listbox fabType = new Listbox();
	private Listbox controlType = new Listbox();
	private Listbox chainList = new Listbox();
	private Window 	blindConfig = null;
	private String fabFamilySelected = null;
	private String fabColourSelected = null;
	private String fabTypeSelected = null;
	private String chainSelected = null;
	private int currentFabSelection = 0;
	private int currentChainSelection = 0;
	private int currOrderLine = 0;
	private int currbldmtomitemlineID = 0;
	private boolean isChainDriven = false;
	private boolean isMtmProdWindow = false;

	
	/*
	 * TODO:Add a 'control' ListBox and populate with product from BOM with Part Type = Tubular blind control
	 * TODO: Modify code in Listbox chainList to add chain options if the 'control' is a chain drive
	 * TODO:Add a 'control' ListBox and populate with product from BOM with Part Type = Tubular blind control
	 * TODO:Add a 'non-control' ListBox and populate with product from BOM with Part Type = Tubular non-control mech
	 * TODO:Add a 'Bracket' ListBox and populate with product from BOM with Part Type = Roller Bracket
	 * TODO:Add a 'Bottom bar' ListBox and populate with product from BOM with Part Type = Bottom bar
	 */
	
	/**
	 * <div>Icons made by <a href="https://www.flaticon.com/authors/smashicons" title="Smashicons">Smashicons</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>
	 */
	public MtmButtonAction() {
	
	}

	@Override
	public void execute(Object target) {
		fabFamilySelected = null;
		fabColourSelected = null;
		fabTypeSelected = null;
		chainSelected = null;
		currentFabSelection = 0;
		currentChainSelection = 0;
		currOrderLine = 0;
		currbldmtomitemlineID = 0;
		isChainDriven = false;
		isMtmProdWindow = false;
		
		ADWindow window = (ADWindow)target;
		ADWindowContent content = window.getADWindowContent();
		tab = content.getActiveGridTab();
		tab.getAD_Window_ID();
		panel = content;
		
		log.info("MtmButtonAction window title: " + window.getTitle());
		tab.getAD_Window_ID();
		tab.getAD_Tab_ID();
		if(window.getTitle().toString().equalsIgnoreCase("Production - made to measure"))isMtmProdWindow = true;
		
		System.out.println("Attempting to parse c_order_line: " + Env.parseContext(Env.getCtx(), tab.getWindowNo(), "@C_OrderLine_ID@", true));
		System.out.println("Attempting to parse bld_mtom_item_line_ID: " + Env.parseContext(Env.getCtx(), tab.getWindowNo(), "@bld_mtom_item_line_ID@", true));
		if(Env.parseContext(Env.getCtx(), tab.getWindowNo(), "@bld_mtom_item_line_ID@", true).length()!=0)
			{
				currbldmtomitemlineID = Integer.parseInt(Env.parseContext(Env.getCtx(), tab.getWindowNo(), "@bld_mtom_item_line_ID@", true));
			}
		
		//Test to see if the current record is a mtm product.
		//Get the c_orderline_id, find the product_id, check if the product_id is a mtm product
		Object mtmAttribute;
		String c_Order_line = Env.parseContext(Env.getCtx(), tab.getWindowNo(), "@C_OrderLine_ID@", true);
		if (c_Order_line ==""){
			FDialog.warn(tab.getWindowNo(), "There's no order line to specify options for.", "Warning");
		}
			else if(c_Order_line != "")
			{
				currOrderLine = Integer.parseInt(c_Order_line);
				StringBuilder sql = new StringBuilder("SELECT m_product_id FROM c_orderline WHERE c_orderline_id = ");
				sql.append(c_Order_line);
				int m_Product = DB.getSQLValue(null, sql.toString());
				
				StringBuilder sql_mtm = new StringBuilder("SELECT ismadetomeasure FROM m_product WHERE m_product_id = ?");
				String isMtm = DB.getSQLValueString(null, sql_mtm.toString(), m_Product);
				if (!isMtm.equals("Y"))
				{
					FDialog.warn(tab.getWindowNo(), "There's no made to measure product to specify options for.", "Warning");
				}
				else //If not mtmproduction window, do below code - check if attributes already assigned
				{
					
					if(isMtmProdWindow&&currbldmtomitemlineID!=0)
					{
						MBLDMtomItemLine thisMtmLine = new MBLDMtomItemLine(Env.getCtx(), currbldmtomitemlineID, null);
						mtmAttribute = thisMtmLine.getinstance_string();
					}
					else
					{
						MOrderLine thisOrderLine = new MOrderLine(Env.getCtx(), currOrderLine, null);
						mtmAttribute = thisOrderLine.get_Value("mtm_attribute");
					}
					  	
					
					//Check if this order line already has attributes assigned.
					
					String patternString = "[0-9][0-9][0-9][0-9][0-9][0-9][0-9]";
				    Pattern pattern = Pattern.compile(patternString);
					
					if(mtmAttribute!=null && mtmAttribute.toString().contains("_"))
					{
						//Split the value of the mtm_attribute column, first value id fabric, second is chain
						String[] products = mtmAttribute.toString().split("_");
						
						Matcher matcher = pattern.matcher(products[0]);
					    boolean matches = matcher.matches();
						if(matches)currentFabSelection = Integer.parseInt(products[0]);
						if(products[0].length() != 7)currentFabSelection = 0;
						
						Matcher matcher1 = pattern.matcher(products[1]);
					    boolean matches1 = matcher1.matches();
						if(matches1)currentChainSelection = Integer.parseInt(products[1]);
					}
					
					show();
				}
			}
		
		
		/*If this returns "" then it fails the check. Should return 'M_Product_ID =1000010' or similar.
		 * Possibly should check for just roller blind?
		 * Maybe create a new category?
		 *
		 */
		
		
	}
	
	public void show(){
		
		fabColour.getItems().clear();
		fabColour.setEnabled(false);
		fabType.getItems().clear();
		fabType.setEnabled(false);
		chainList.getItems().clear();
		try
		{
			chainList.setEnabled(false);
		}
		catch(NullPointerException ex)
		{
			System.out.println("Whoopsie: " + ex.toString());
		}
		//TODO: Set enabled(true) if the item is chain controlled.	
		fabFamily.setMold("select");
		fabFamily.getItems().clear();
		initFabFam();
		
		if (blindConfig != null)blindConfig=null;
		{	
		blindConfig = new Window();
		ZKUpdateUtil.setWidth(blindConfig, "650px");
		blindConfig.setClosable(true);
		blindConfig.setBorder("normal");
		blindConfig.setStyle("position:absolute");
		blindConfig.addEventListener("onValidate", this);

		Vbox vb = new Vbox();
		ZKUpdateUtil.setWidth(vb, "100%");
		blindConfig.appendChild(vb);
		blindConfig.setSclass("toolbar-popup-window");
		vb.setSclass("toolbar-popup-window-cnt");
		vb.setAlign("stretch");
		
		Grid grid = GridFactory.newGridLayout();
		vb.appendChild(grid);
        
        Columns columns = new Columns();
        Column column = new Column();
        ZKUpdateUtil.setHflex(column, "min");
        columns.appendChild(column);
        column = new Column();
        ZKUpdateUtil.setHflex(column, "1");
        columns.appendChild(column);
        grid.appendChild(columns);
        
        Rows rows = new Rows();
		grid.appendChild(rows);
			
		
		//Add the fabFam list
		Row row = new Row();
		rows.appendChild(row);
		row.appendChild(new Label("Fabric Family:"));
		row.appendChild(fabFamily);
		ZKUpdateUtil.setHflex(fabFamily, "1");
		fabFamily.addEventListener(Events.ON_SELECT, this);
	
		
		//Add the fabColour Listbox
		Row row1 = new Row();
		rows.appendChild(row1);
		row1.appendChild(new Label("Fabric Colour:")); 
		row1.appendChild(fabColour);
		ZKUpdateUtil.setHflex(fabColour, "1");
		if(currentFabSelection == 0 && fabFamilySelected == null)
			{
			fabColour.setVisible(false);//Don't show this box until a fabric family is selected.
			}
		fabColour.addEventListener(Events.ON_SELECT, this);
		
		//Add the fabType Listbox
		Row row2 = new Row();
		rows.appendChild(row2);
		row2.appendChild(new Label("Fabric Type:")); 
		row2.appendChild(fabType);
		ZKUpdateUtil.setHflex(fabType, "1");
		fabType.addEventListener(Events.ON_SELECT, this);
		if(currentFabSelection == 0 && fabColourSelected == null)fabType.setVisible(false);//Don't show this Listbox until a fabric colour is selected. 
		
		//Add control Listbox
		Row row3 = new Row();
		rows.appendChild(row3);
		row3.appendChild(new Label("Control type:")); 
		row3.appendChild(controlType);
		ZKUpdateUtil.setHflex(controlType, "1");
		controlType.addEventListener(Events.ON_SELECT, this);
		
		
		/*
		 * Add chainFam Listbox
		 * Check if it's chain driven
		 */
		isChainDriven = checkChainDrive();
		if(isChainDriven)
		{
			Row row21 = new Row();
			rows.appendChild(row21);
			row21.appendChild(new Label("Chain:")); 
			row21.appendChild(chainList);
			ZKUpdateUtil.setHflex(chainList, "1");
			chainList.addEventListener(Events.ON_SELECT, this);
			initChains();
		}
		
		
		//Add confirm panel
		vb.appendChild(confirmPanel);
		LayoutUtils.addSclass("dialog-footer", confirmPanel);
		confirmPanel.addActionListener(this);
		
		
		LayoutUtils.openPopupWindow(panel.getToolbar().getButton("mtmb"), blindConfig, "after_start");
		
		}
	}
	
	private void loadFabColour(String m_Product_id)
	{
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT m_product_id, description");
		sql.append(" FROM m_product");
		sql.append(" WHERE name = ");
		sql.append("(SELECT name FROM m_product WHERE m_product_id = ");
		sql.append(m_Product_id);
		sql.append(")");
		
		fabColour.setMold("select");
		ArrayList<String> dupCheck = new ArrayList<String>();
		KeyNamePair[] keyNamePairs = DB.getKeyNamePairs(sql.toString(), true);
		
		KeyNamePair checkFab = checkSelection(keyNamePairs, currentFabSelection);
		boolean addCurrent = false;
		if (checkFab != null)
			{
			dupCheck.add(checkFab.getName());
			addCurrent = true;
			}
		
		int count = 0;
		for (KeyNamePair pair : keyNamePairs) 
		{	
		
			if(count  == 1 && addCurrent)fabColour.appendItem(checkFab.getName(), checkFab.getID());//adds the current selection at index 1
		
			//Remove duplicfabColourSelectedates
				if (!dupCheck.contains(pair.getName())) fabColour.appendItem(pair.getName(), pair.getID());
				dupCheck.add(pair.getName());
				count++;
		}
		
		
		if (currentFabSelection !=0)
		{
			fabColour.setSelectedIndex(1);//Initializes the list with the value from the DB
			fabColour.setEnabled(true);
			fabColour.setVisible(true);
			loadFabType(Integer.toString(currentFabSelection));
			
		}
		
	   }
	
	
	private KeyNamePair checkSelection(KeyNamePair[] keyNamePairs, int currentSelection)
	{
	if(currentSelection !=0)//There's already a selection, add it to the list at index 1
	{
		for (KeyNamePair pair : keyNamePairs)
		{
			if(pair.getKey() > 1)
			{
				if(pair.getID().equals(Integer.toString(currentSelection)))return pair;
			}
				
			
		}
	}return null;
	}
	
	
	
	
	
	private void initFabFam() {
		
		   //Get the values for the fabric list box.
		
		String sql = "SELECT m_product_id, name"
				+ " FROM m_product as mp"
				+ " WHERE mp.m_parttype_id ="
				+ " (SELECT m_parttype_id FROM m_parttype WHERE m_parttype.name = 'Fabric')";
				
		fabFamily.setMold("select");
			KeyNamePair[] keyNamePairs = DB.getKeyNamePairs(sql, true);
			ArrayList<String> dupCheck = new ArrayList<String>();
			boolean addCurrent = false;
			int count = 0;
			
			KeyNamePair checkFab = checkSelection(keyNamePairs, currentFabSelection);
			if (checkFab != null)
				{
				dupCheck.add(checkFab.getName());
				addCurrent = true;
				}
			
			for (KeyNamePair pair : keyNamePairs) 
			{	
				System.out.println("Before check "+ pair.getID());
				if(count == 1 && addCurrent)fabFamily.appendItem(checkFab.getName(), checkFab.getID());//adds the current selection at index 1
				
				if (!dupCheck.contains(pair.getName())) fabFamily.appendItem(pair.getName(), pair.getID());//Remove duplicates
					
					dupCheck.add(pair.getName());
					count++;
			}
			
			if(currentFabSelection != 0)
				{
				fabFamily.setSelectedIndex(1);//Sets the list with the value from the DB
				loadFabColour(Integer.toString(currentFabSelection));
				fabFamilySelected = Integer.toString(currentFabSelection);
				}
				
			
			}
	
	
	private void loadFabType(String selectedColour)
	{
		if(currentFabSelection != 0)
		{
			fabFamilySelected = Integer.toString(currentFabSelection);
		}
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT mp.m_product_id, ma.value ");
		sql.append("FROM m_product mp ");
		sql.append("INNER JOIN m_attributeinstance ma ");
		sql.append("ON mp.m_attributesetinstance_id = ma.m_attributesetinstance_id ");
		sql.append("AND mp.name = (SELECT name FROM m_product WHERE m_product_id = ");
		sql.append(fabFamilySelected);
		sql.append(") ");
		sql.append("AND mp.description = (SELECT description FROM m_product WHERE m_product_id = ");
		sql.append(selectedColour);
		sql.append(")");
		
		fabType.setMold("select");
		KeyNamePair[] keyNamePairs = DB.getKeyNamePairs(sql.toString(), true);
		ArrayList<String> dupCheck = new ArrayList<String>();
		
		KeyNamePair checkFab = checkSelection(keyNamePairs, currentFabSelection);
		boolean addCurrent = false;
		if (checkFab != null)
			{
			addCurrent = true;
			dupCheck.add(checkFab.getName());
			}
		
		int count = 0;
		for (KeyNamePair pair : keyNamePairs) 
		{	
		
			if(count  == 1 && addCurrent)fabType.appendItem(checkFab.getName(), checkFab.getID());//adds the current selection at index 1
		
			//Remove duplicfabColourSelectedates
			if (!dupCheck.contains(pair.getName())) fabType.appendItem(pair.getName(), pair.getID());//Remove duplicates
			
			dupCheck.add(pair.getName());
			count++;
		}
		
		if (currentFabSelection !=0)
		{
			fabType.setSelectedIndex(1);//Initializes the list with the value from the DB
			fabType.setEnabled(true);
			fabType.setVisible(true);
			
		}
		
		int fabTypeItemCount = fabType.getItemCount();
		if(fabTypeItemCount <3)fabType.setEnabled(false);//If only one item, select it and set box unable
			if (fabTypeItemCount >1 && fabTypeItemCount <3)
			{
				fabType.setSelectedIndex(1);
				fabTypeSelected = selectedColour;
			}
			if (fabTypeItemCount <=1)FDialog.warn(tab.getWindowNo(), "Fabric type not determined, check product setup.", "Warning");
			
	}
	
	private void initChains()//We want the chains to be on the BOM for the product being optioned.
	{
		if(currentChainSelection != 0)
		{
			chainSelected = Integer.toString(currentChainSelection);
		}
		
		StringBuilder sql = new StringBuilder("SELECT m_product_id, CONCAT(name, ' ', description) AS Summary ");
		sql.append("FROM m_product mp ");
		sql.append("WHERE mp.m_parttype_id = ");
		sql.append("(SELECT m_parttype_id FROM m_parttype WHERE m_parttype.name = 'Chain');");
		
		chainList.setMold("select");
		KeyNamePair[] keyNamePairs = DB.getKeyNamePairs(sql.toString(), true);
		ArrayList<String> dupCheck = new ArrayList<String>();
		
		KeyNamePair checkChains = checkSelection(keyNamePairs, currentChainSelection);
		boolean addCurrent = false;
		if (checkChains != null)
			{
			addCurrent = true;
			dupCheck.add(checkChains.getName());
			}
		
		int count = 0;
		for (KeyNamePair pair : keyNamePairs) 
		{	
		
			if(count  == 1 && addCurrent)chainList.appendItem(checkChains.getName(), checkChains.getID());//adds the current selection at index 1
		
			//Remove duplicfabColourSelectedates
			if (!dupCheck.contains(pair.getName())) chainList.appendItem(pair.getName(), pair.getID());//Remove duplicates
			
			dupCheck.add(pair.getName());
			count++;
		}
		
		if (currentChainSelection !=0)
		{
			chainList.setSelectedIndex(1);//Initializes the list with the value from the DB
		}
		
		chainList.setEnabled(true);
		chainList.setVisible(true);
		
		
	}

	private void validate() throws Exception
	{
		/*
		 * Validate
		 * Don't update the DB unless there are complete values
		 * For fabric, fabTypeSelected, which is the last fabric config option, MUST have a value
		 * if it is to be written to DB.
		 * For chains, the last field in the dialog must have been given a value. If the blind is not
		 * chain driven, then "" is to be passed
		 */
		
	//If all OK then:
		if(currentFabSelection==0)
		{
			FDialog.warn(tab.getWindowNo(), "Fabric not selected.", "Warning");//Cannot proceed without fabric
		}
		else 
		{
			setCurrentSelection(currentFabSelection,currentChainSelection);
		}
	}
	
	@Override
	public void onEvent(Event event) throws Exception {
		
		if(event.getTarget().getId().equals(ConfirmPanel.A_CANCEL))
			
			blindConfig.onClose();
			
			
		
		else if(event.getTarget().getId().equals(ConfirmPanel.A_OK)) {
			
			if(fabTypeSelected!=null){
				if(Integer.parseInt(fabTypeSelected)!=0)currentFabSelection = Integer.parseInt(fabTypeSelected);
			}
			
			
			blindConfig.setVisible(false);
			Clients.showBusy(Msg.getMsg(Env.getCtx(), "Processing"));
			Events.echoEvent("onValidate", blindConfig, null);

		}
		
		else if (event.getTarget() == fabFamily)
		{
			fabFamilySelected = null;
			ListItem li = fabFamily.getSelectedItem();
			fabFamilySelected = li.getValue();
	
			
			
			//Enable and populate the fabColour Listbox
			fabColour.removeAllItems();
			fabType.removeAllItems();
			fabType.setEnabled(false);
			fabType.setVisible(false);
			fabColour.setEnabled(true);
			fabColour.setVisible(true);
			currentFabSelection = 0;
			loadFabColour(fabFamilySelected);
			setOkButton();
			
		}
		
		else if(event.getTarget() == fabColour)
		{
			//Enable and populate the fabColour Listbox
			fabColourSelected = null;
			ListItem li = fabColour.getSelectedItem();
			fabColourSelected = li.getValue();
			
			fabType.removeAllItems();
			fabType.setEnabled(true);
			fabType.setVisible(true);
			currentFabSelection = 0;
			loadFabType(fabColourSelected);
			setOkButton();
		}
		
		else if(event.getTarget() == fabType)
		{
		
			fabTypeSelected = null;
			ListItem li = fabType.getSelectedItem();
			fabTypeSelected = li.getValue();
			
			if(fabTypeSelected == null)
			{
				confirmPanel.setEnabled("OK", false);
			}
			else 
				{
				currentFabSelection = Integer.parseInt(fabTypeSelected);
				confirmPanel.setEnabled("OK", false);
				}
			setOkButton();
		}
		
		else if(event.getTarget() == chainList)
		{
			chainSelected = null;
			ListItem li = chainList.getSelectedItem();
			chainSelected = li.getValue();
			
			if (chainSelected != null)currentChainSelection = Integer.parseInt(chainSelected );
			setOkButton();
		}
		
		else if (event.getName().equals("onValidate"))
		{
			try {
				validate();
			} finally {
				Clients.clearBusy();
				panel.getComponent().invalidate();
			}
		
		/*TODO: Get the current order line/production item PK. When ONCLose is called:
		 * *Create an instance attribute object - get c_orderline.mattributeinstance_id and create the object.
		 * Try appending on to the 'description' field?
		 */
	}

	}
	
	private void setOkButton()
	{
		confirmPanel.setEnabled("Ok", true);
		ListItem li = fabType.getSelectedItem();
		if(li != null)fabTypeSelected = li.getValue();
		
		if(fabTypeSelected == null||li == null)
		{
			confirmPanel.setEnabled("Ok", false);
		}
		
		ListItem li2 = chainList.getSelectedItem();
		if(li2 != null)chainSelected = li2.getValue();
		
		if (chainSelected == null & isChainDriven)
			{
			confirmPanel.setEnabled("Ok", false);
			}
	}
	
	public int getCurrentselection()
	{
		return currentFabSelection;//TODO: Change to something that makes sense.
		
	}
	
	private void setCurrentSelection(final int lastFabric, final int lastChain)
	{
		//set the mtm_attribute column, but may need to get current contents then make new. Nah, just overwrite it.
		//TODO: Modify to allow use in the context of mtmProduction window.
	        Trx.run(new TrxRunnable() {
	            public void run(String trxName) {
	            	
	        		StringBuilder replaceValue = new StringBuilder(Integer.toString(lastFabric));
	        		replaceValue.append("_");
	        		replaceValue.append(Integer.toString(lastChain));
	        		String look = replaceValue.toString();
	        		
	        		if(currbldmtomitemlineID != 0)//It's an mtmproductionline
	        		{
	        			MBLDMtomItemLine thisMtmLine = new MBLDMtomItemLine(Env.getCtx(), currbldmtomitemlineID, null);
						thisMtmLine.setinstance_string(replaceValue.toString());
						thisMtmLine.saveEx();
	        		}
	        		else
	        		{
	        			MOrderLine thisOrderLine = new MOrderLine(Env.getCtx(), currOrderLine, null);
	        			thisOrderLine.set_ValueOfColumn("mtm_attribute", replaceValue.toString());
	        			thisOrderLine.saveEx();
	        		}
	        	
	        		System.out.println(look);
	        
	            }
	      
	        });
	}
	

	
	private boolean checkChainDrive()//TODO: Modify to suit 
	{
		MOrderLine thisOrderLine = new MOrderLine(Env.getCtx(), currOrderLine, null);
		int attInsID = thisOrderLine.getM_AttributeSetInstance_ID();
		StringBuilder sql = new StringBuilder("SELECT description ");
		sql.append("FROM m_attributesetinstance ");
		sql.append("WHERE m_attributesetinstance_id = ?");
		
		//Get instance attribute for this lineitem.
		String description = DB.getSQLValueString(null, sql.toString(), attInsID);
		
		String word = "chain";
		Boolean found;
		found = description.contains(word);
		System.out.println(description + found.toString());
		
		return found;
	}
	
	private void setOptions(int m_product_id) 
	{
		/*DB.getRowSet(sql) or getKeynamePair()?
		*Get BOM: SELECT mp.m_parttype_id, mp.m_product_id FROM m_product mp INNER JOIN m_product_bom mpb ON mp.m_product_id = mpb.m_productbom_id AND mpb.m_product_id = '1000010';
		*or SELECT mp.name, mp.m_product_id, mp.m_parttype_id FROM m_product mp INNER JOIN m_product_bom mpb ON mp.m_product_id = mpb.m_productbom_id AND mpb.m_product_id = '1000010';
		*Figure out which BOM items are: Chain, control, roller bracket, bottom bar, TNCM (tubular non control mech)
		*/
	}
}

