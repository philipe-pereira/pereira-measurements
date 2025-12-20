package br.com.pereiraeng.measurements.manegement;

import br.com.pereiraeng.io.Comm;
import br.com.pereiraeng.io.Comm.DataSourceType;

/**
 * Classe do objeto que consulta a fonte de dados de medição de modo a informar
 * se ela está ativa ou não
 * 
 * @author Philipe PEREIRA
 *
 */
public class MedPing {

	private boolean active = false;

	private boolean forceStop = false;

	private DataSourceType type;

	private Object[] params;

	public void setType(DataSourceType type) {
		this.type = type;
	}

	public void setParams(Object... params) {
		this.params = params;
	}

	public boolean check(String system) {
		return active = Comm.check(system, type, true, params);
	}

	public boolean isActive() {
		return active;
	}

	public void setForceStop(boolean forceStop) {
		this.forceStop = forceStop;
	}

	public boolean isForceStop() {
		return forceStop;
	}

}
