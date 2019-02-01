package au.blindmot.factories;

import java.sql.ResultSet;

import org.adempiere.base.IModelFactory;
import org.compiere.model.PO;
import org.compiere.util.Env;

import au.blindmot.model.MBLDBomDerived;
import au.blindmot.model.MBLDLineProductInstance;
import au.blindmot.model.MBLDLineProductSetInstance;
import au.blindmot.model.MBLDMtmScan;
import au.blindmot.model.MBLDMtomCuts;
import au.blindmot.model.MBLDMtomItemLine;
import au.blindmot.model.MBLDMtomProduction;
import au.blindmot.model.MBLDProductPartType;
import au.blindmot.model.MBLDProductionLog;

public class BLDMtomModelFactory implements IModelFactory {

	@Override
	public Class<?> getClass(String tableName) {
		

		if(tableName.equalsIgnoreCase(MBLDMtomItemLine.Table_Name))
			return MBLDMtomItemLine.class;
			
		if(tableName.equalsIgnoreCase(MBLDMtomProduction.Table_Name))
			return MBLDMtomProduction.class;
		
		if(tableName.equalsIgnoreCase(MBLDBomDerived.Table_Name))
			return MBLDBomDerived.class;

		if(tableName.equalsIgnoreCase(MBLDMtomCuts.Table_Name))
			return MBLDMtomCuts.class;
		
		if(tableName.equalsIgnoreCase(MBLDProductionLog.Table_Name))
			return MBLDProductionLog.class;
		
		if(tableName.equalsIgnoreCase(MBLDLineProductSetInstance.Table_Name))
			return MBLDLineProductSetInstance.class;
		
		if(tableName.equalsIgnoreCase(MBLDLineProductInstance.Table_Name))
			return MBLDLineProductInstance.class;
		
		if(tableName.equalsIgnoreCase(MBLDProductPartType.Table_Name))
			return MBLDProductPartType.class;
		
		if(tableName.equalsIgnoreCase(MBLDMtmScan.Table_Name))
			return MBLDMtmScan.class;
		
		return null;
	}

	@Override
	public PO getPO(String tableName, int Record_ID, String trxName) {
		
		if(tableName.equalsIgnoreCase(MBLDMtomItemLine.Table_Name))
		return new MBLDMtomItemLine(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDMtomProduction.Table_Name))
			return new MBLDMtomProduction(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDBomDerived.Table_Name))
			return new MBLDBomDerived(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDMtomCuts.Table_Name))
			return new MBLDMtomCuts(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDProductionLog.Table_Name))
			return new MBLDProductionLog(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDLineProductSetInstance.Table_Name))
			return new MBLDLineProductSetInstance(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDLineProductInstance.Table_Name))
			return new MBLDLineProductInstance(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDProductPartType.Table_Name))
			return new MBLDProductPartType(Env.getCtx(), Record_ID, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDMtmScan.Table_Name))
			return new MBLDMtmScan(Env.getCtx(), Record_ID, trxName);
		
		return null;
	}

	@Override
	public PO getPO(String tableName, ResultSet rs, String trxName) {
		
		if(tableName.equalsIgnoreCase(MBLDMtomItemLine.Table_Name))
			return new MBLDMtomItemLine(Env.getCtx(), rs, trxName);
			
		if(tableName.equalsIgnoreCase(MBLDMtomProduction.Table_Name))
			return new MBLDMtomItemLine(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDBomDerived.Table_Name))
			return new MBLDBomDerived(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDMtomCuts.Table_Name))
			return new MBLDMtomCuts(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDProductionLog.Table_Name))
			return new MBLDProductionLog(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDLineProductSetInstance.Table_Name))
			return new MBLDLineProductSetInstance(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDLineProductInstance.Table_Name))
			return new MBLDLineProductInstance(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDProductPartType.Table_Name))
			return new MBLDProductPartType(Env.getCtx(), rs, trxName);
		
		if(tableName.equalsIgnoreCase(MBLDMtmScan.Table_Name))
			return new MBLDMtmScan(Env.getCtx(), rs, trxName);
		
		return null;
	}

}
