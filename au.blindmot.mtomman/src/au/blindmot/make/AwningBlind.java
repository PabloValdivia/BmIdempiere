package au.blindmot.make;

import org.compiere.model.MAttributeSetInstance;

public class AwningBlind extends MadeToMeasureProduct {

	/**
	 * 
	 * @param product_id
	 * @param bld_mtom_item_line_id
	 */
	public AwningBlind (int product_id, int bld_mtom_item_line_id) {
		super(product_id, bld_mtom_item_line_id);
	}
	
	@Override
	public void interpretMattributeSetInstance() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean getCuts() {
		return false;
		
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean createBomDerived() {
		return false;
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean createProductionLine() {
		return false;
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean deleteBomDerived() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteCuts() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteProductionLine() {
		// TODO Auto-generated method stub
		return false;
	}

}