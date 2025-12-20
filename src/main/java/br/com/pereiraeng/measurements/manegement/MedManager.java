package br.com.pereiraeng.measurements.manegement;

import java.util.LinkedList;
import java.util.List;

/**
 * Classe do objeto que gerencia os sistema de aquisição e publicação de
 * medições
 * 
 * @author Philipe PEREIRA
 * @version July 30, 2020
 */
public class MedManager {

	protected List<MedSystem> medSystems;

	public MedManager() {
		medSystems = new LinkedList<>();
	}

	public void addMedSystem(MedSystem medSystem) {
		this.medSystems.add(medSystem);
	}

	public void refresh() {
		for (MedSystem ms : medSystems)
			ms.refresh();
	}

	protected MedSystem getMedSystem(String name) {
		MedSystem out = null;
		for (MedSystem ms : medSystems) {
			if (name.equals(ms.toString())) {
				out = ms;
				break;
			}
		}
		return out;
	}
}
