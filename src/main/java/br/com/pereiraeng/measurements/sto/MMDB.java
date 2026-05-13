package br.com.pereiraeng.measurements.sto;

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import br.com.pereiraeng.core.Flow;
import br.com.pereiraeng.math.timeseries.unit.Med;
import br.com.pereiraeng.sql.SQLadapter;

/**
 * <p>
 * <strong>M</strong>ultiple <strong>M</strong>easurements
 * <strong>D</strong>ata<strong>B</strong>ase
 * </p>
 * 
 * @author Philipe PEREIRA
 *
 */
public class MMDB extends MDB {

	protected SQLadapter[] auxDbs;

	protected MMDB(SQLadapter... dbs) {
		super(dbs[0]);
		if (dbs.length > 1) {
			this.auxDbs = new SQLadapter[dbs.length - 1];
			System.arraycopy(dbs, 1, this.auxDbs, 0, this.auxDbs.length);
		}
		lastMedidoresFound = new HashSet<>();
	}

	@Override
	protected void get(Flow<Med> flow, String timeWhere, Map<Object, Map<Integer, Set<Integer>>> channels) {

		super.get(flow, timeWhere, channels);

		Map<Object, Map<Integer, Set<Integer>>> remainingChannels = new HashMap<>(channels);

		removeSuccessfulDownloaded(remainingChannels);

		if (remainingChannels.size() > 0) {
			Connection main = super.getConn();
			for (int i = 0; i < auxDbs.length; i++) {
				super.setConn(auxDbs[i].getConn());

				super.get(flow, timeWhere, remainingChannels);
				removeSuccessfulDownloaded(remainingChannels);

				if (remainingChannels.size() == 0)
					break;
			}
			super.setConn(main);
		}
	}

	private void removeSuccessfulDownloaded(Map<Object, Map<Integer, Set<Integer>>> remainingChannels) {
		Iterator<Entry<Object, Map<Integer, Set<Integer>>>> it = remainingChannels.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Object, Map<Integer, Set<Integer>>> e = it.next();
			if (lastMedidoresFound.contains(e.getKey()))
				it.remove();
		}
	}
}
