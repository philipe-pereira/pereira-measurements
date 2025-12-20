package br.com.pereiraeng.measurements.sto;

import br.com.pereiraeng.sql.SQLadapter;

/**
 * <p>
 * <strong>M</strong>ulti-<strong>T</strong>ables <strong>M</strong>easurements
 * <strong>D</strong>ata<strong>B</strong>ase
 * </p>
 * 
 * @author Philipe PEREIRA
 *
 */
public abstract class MTMDB extends MDB {

	public MTMDB(SQLadapter sql) {
		super(sql);
	}

	protected transient int syst = 0;

	protected transient int table = 0;

	public void setSystTable(int syst, int table) {
		this.syst = syst;
		this.table = table;
	}
}
