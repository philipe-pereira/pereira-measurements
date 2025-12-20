package br.com.pereiraeng.measurements.label;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Collection;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;

import br.com.pereiraeng.physics.Grandeza;
import br.com.pereiraeng.swing.SwingUtils;
import br.com.pereiraeng.swing.table.ListSetTableModel;

public class TagTable extends JScrollPane {
	private static final long serialVersionUID = 1L;

	private ListSetTableModel<EltMedEtq> rotm;

	private JTable t;

	public TagTable() {
		this.rotm = new ListSetTableModel<>(EltMedEtq.HEADER);
		this.rotm.setColumnClass(Grandeza.class, 2);

		this.t = new JTable(rotm);
		SwingUtils.setColumnsWidth(t, new int[] { 90, 10, 44 });
		t.setDefaultRenderer(Grandeza.class, new GrandezaRenderer());

		setViewportView(t);
		setPreferredSize(new Dimension(160, 290));
	}

	public void setHeight(int height) {
		setPreferredSize(new Dimension(getPreferredSize().width, height));
	}

	public void add(EltMedEtq t) {
		rotm.add(t);
	}

	public void clear() {
		rotm.clear();
	}

	/**
	 * Usar esse método quando não se quer modificar uma lista que fora enviada pelo
	 * método {@link #setTagList(Collection<EltMedEtq>)}, somente o objeto gráfico
	 */
	public void empty() {
		rotm.empty();
	}

	public void setTagList(Collection<EltMedEtq> tagList) {
		this.rotm.setCollection(tagList);
	}

	public TagCollection<EltMedEtq> getTagList() {
		Collection<EltMedEtq> c = this.rotm.getCollection();
		return (TagCollection<EltMedEtq>) c;
	}

	public void addListSelectionListener(ListSelectionListener lsl) {
		t.getSelectionModel().addListSelectionListener(lsl);
	}

	public EltMedEtq[] getSelectedTags() {
		int[] rows = t.getSelectedRows();
		EltMedEtq[] tags = new EltMedEtq[rows.length];
		for (int i = 0; i < rows.length; i++)
			tags[i] = rotm.get(rows[i]);
		return tags;
	}

	/**
	 * Classe do objeto gráfico que será a célula da tabela indicando a grandeza
	 * medida por uma tag desta subestação
	 * 
	 * @author Philipe PEREIRA
	 *
	 */
	public static class GrandezaRenderer extends JLabel implements TableCellRenderer {
		private static final long serialVersionUID = 1L;

		private Grandeza grandeza;

		public GrandezaRenderer() {
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		protected void paintComponent(Graphics g) {
			if (this.grandeza != null) {
				Color c = g.getColor();
				g.setColor(this.grandeza.getColor());
				g.fillOval(12, 0, 15, 15);
				g.setColor(c);
			}
			super.paintComponent(g);
		}

		public void setGrandeza(Grandeza value) {
			this.grandeza = value;
			super.setText(this.grandeza != null ? this.grandeza.name().charAt(0) + "" : "");
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			this.setGrandeza((Grandeza) value);
			return this;
		}
	}
}