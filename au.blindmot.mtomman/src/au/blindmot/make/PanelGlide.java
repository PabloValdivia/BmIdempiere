package au.blindmot.make;

public class PanelGlide extends MadeToMeasureProduct {
	

	/**
	 * 
	 * @param product_id
	 * @param bld_mtom_item_line_id
	 */
	public PanelGlide (int product_id, int bld_mtom_item_line_id) {
		super(product_id, bld_mtom_item_line_id);
	}

	@Override
	public boolean getCuts() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean createBomDerived() {
		return false;
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void interpretMattributeSetInstance() {
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

}
