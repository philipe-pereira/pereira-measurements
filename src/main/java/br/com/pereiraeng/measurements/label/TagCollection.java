package br.com.pereiraeng.measurements.label;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import br.com.pereiraeng.physics.Grandeza;

/**
 * Classe dos objetos que representam uma lista de tags e que provê métodos para
 * organizar tais tags (organizar por equipamento, grandeza, nível de tensão,
 * etc.)
 * 
 * @author Philipe PEREIRA
 *
 */
public class TagCollection<K extends EltMedEtq> extends LinkedList<K> {
	private static final long serialVersionUID = -7361858341754901689L;

	protected List<Grandeza> grands;

	public TagCollection(Grandeza... gs) {
		this.grands = Arrays.asList(gs);
	}

	public TagCollection(Collection<? extends K> tags) {
		super(tags);
	}

	public List<Grandeza> getGrands() {
		return grands;
	}
}