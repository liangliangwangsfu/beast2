package beast.app.beauti;

import static beast.util.AddOnManager.NO_CONNECTION_MESSAGE;
import static beast.util.AddOnManager.beastVersion;
import static beast.util.AddOnManager.getPackageSystemDir;
import static beast.util.AddOnManager.getPackageUserDir;
import static beast.util.AddOnManager.getPackages;
import static beast.util.AddOnManager.getToDeleteListFile;
import static beast.util.AddOnManager.installPackage;
import static beast.util.AddOnManager.uninstallPackage;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import beast.core.Description;
import beast.util.AddOnManager;
import beast.util.Package;

/**
 * dialog for managing Package.
 * List, install and uninstall Package
 *
 * @author  Remco Bouckaert
 * @author  Walter Xie
 */
@Description("BEAUti package manager")
public class JPackageDialog extends JPanel {
    private static final long serialVersionUID = 1L;
    JScrollPane scrollPane;
    JLabel jLabel;
    Box buttonBox;
    JCheckBox allDepCheckBox = new JCheckBox("install/uninstall all dependencies", null, true);
    JFrame frame;
    JTable dataTable = null;

    List<Package> packages = new ArrayList<>();

    boolean isRunning;
    Thread t;
    
    public JPackageDialog() {
        jLabel = new JLabel("List of available packages for BEAST v" + beastVersion.getMajorVersion() + ".*");
        frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        setLayout(new BorderLayout());

		createTable();
        // update packages using a 30 second time out
        isRunning = true;
        t = new Thread() {
        	@Override
			public void run() {
                resetPackages();
        		isRunning = false;
        	}
        };
        t.start();
    	Thread t2 = new Thread() {
    		@Override
			public void run() {
    			try {
    				// wait 30 seconds
					sleep(30000);
	    			if (isRunning) {
	    				t.interrupt();
	    				JOptionPane.showMessageDialog(frame, "<html>Download of file " +
	    						AddOnManager.PACKAGES_XML + " timed out.<br>" +
	    								"Perhaps this is due to lack of internet access</br>" +
	    								"or some security settings not allowing internet access.</html>"
	    						);
	    			}
				} catch (InterruptedException e) {
				}
    		};
    	};
    	t2.start();
        
        try {
			t.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        	
        scrollPane = new JScrollPane(dataTable);
        /*getContentPane().*/add(BorderLayout.CENTER, scrollPane);

        buttonBox = createButtonBox();
        /*getContentPane().*/add(buttonBox, BorderLayout.SOUTH);

        scrollPane.setPreferredSize(new Dimension(660, 400));
        Dimension dim = scrollPane.getPreferredSize();
        Dimension dim2 = buttonBox.getPreferredSize();
        setSize(dim.width + 30, dim.height + dim2.height + 30);
    }


    private void createTable() {
        DataTableModel dataTableModel = new DataTableModel();
        dataTable = new JTable(dataTableModel);
        
        double [] widths = new double[dataTable.getColumnCount()];
        //double total = 0;
        for (int i = 0; i < dataTable.getColumnCount(); i++) {
        	widths[i] = dataTable.getColumnModel().getColumn(i).getWidth();
        	//total += widths[i]; 
        }
        widths[2] /= 4.0;
        dataTable.getColumnModel().getColumn(2).setPreferredWidth((int) widths[2]);
        dataTable.getColumnModel().getColumn(2).setMinWidth((int) widths[2]);
        widths[3] /= 2.0; 
        dataTable.getColumnModel().getColumn(3).setPreferredWidth((int) widths[3]);
        widths[4] *= 2.0; 
        dataTable.getColumnModel().getColumn(4).setPreferredWidth((int) widths[4]);
        
        
        // TODO:
        // The following would work ...
        //dataTable.setAutoCreateRowSorter(true);
        // ...if all processing was done based on the data in the table, 
        // instead of the row number alone.
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dataTable.addMouseListener(new MouseAdapter() {
            @Override
			public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Package selPackage = getSelectedPackage(dataTable.getSelectedRow());
                    showDetail(selPackage);
                }
            }
        });
    }

    private void resetPackages() {
        packages.clear();
        try {
            packages = getPackages();
        } catch (AddOnManager.PackageListRetrievalException e) {
        	StringBuilder msgBuilder = new StringBuilder("<html>" + e.getMessage() + "<br>");
            if (e.getCause() instanceof IOException)
                msgBuilder.append(NO_CONNECTION_MESSAGE.replaceAll("\\.", ".<br>"));
            msgBuilder.append("</html>");

        	try {
        	SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, msgBuilder));
        	} catch (Exception e0) {
        		e0.printStackTrace();
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }

        dataTable.tableChanged(new TableModelEvent(dataTable.getModel()));

        if (dataTable.getRowCount() > 0)
            dataTable.setRowSelectionInterval(0, 0);

    }

    private Package getSelectedPackage(int selectedRow) {
        if (packages.size() <= selectedRow)
            throw new IllegalArgumentException("Incorrect row " + selectedRow +
                    " is selected from package list, size = " + packages.size());
        return packages.get(selectedRow);
    }

    private void showDetail(Package aPackage) {
        //custom title, no icon
        JOptionPane.showMessageDialog(null,
                aPackage.toHTML(),
                aPackage.packageName,
                JOptionPane.PLAIN_MESSAGE);
    }

    private Box createButtonBox() {
        Box box = Box.createHorizontalBox();
        box.add(allDepCheckBox);
        box.add(Box.createGlue());
        JButton installButton = new JButton("Install/Upgrade");
        installButton.addActionListener(e -> {
            	// first get rid of existing packages
            	StringBuilder removedPackageNames = new StringBuilder();
            	doUninstall(removedPackageNames);

                int[] selectedRows = dataTable.getSelectedRows();
                StringBuilder installedPackageNames = new StringBuilder();

                for (int selRow : selectedRows) {
                    Package selPackage = getSelectedPackage(selRow);
                    if (selPackage != null) {
                        try {
//                            if (selPackage.isInstalled()) {
//                                //TODO upgrade version
//                            } else {
                                setCursor(new Cursor(Cursor.WAIT_CURSOR));
                                if (allDepCheckBox.isSelected()) {
                                    installPackage(selPackage, false, null, packages);
                                } else {
                                    installPackage(selPackage, false, null, null);
                                }
                                if (installedPackageNames.length()>0)
                                    installedPackageNames.append(", ");
                                installedPackageNames.append("'")
                                        .append(selPackage.packageName)
                                        .append("'");

                                setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
//                            }
                            resetPackages();
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(null, "Install failed because: " + ex.getMessage());
                            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        }
                    }
                }

                if (installedPackageNames.length()>0)
                    JOptionPane.showMessageDialog(null, "Package(s) "
                            + installedPackageNames.toString() + " installed. "
                            + "Note that any changes to the BEAUti "
                            + "interface will\n not appear until a "
                            + "new document is created or BEAUti is "
                            + "restarted.");
            });
        box.add(installButton);

        JButton uninstallButton = new JButton("Uninstall");
        uninstallButton.addActionListener(e -> {
            	StringBuilder removedPackageNames = new StringBuilder();
            	boolean toDeleteFileExists = doUninstall(removedPackageNames);
                resetPackages();

                if (toDeleteFileExists) {
                    JOptionPane.showMessageDialog(null, "<html>To complete uninstalling the package, BEAUti need to be restarted<br><br>Exiting now.</html>");
                    System.exit(0);
                }

                if (removedPackageNames.length()>0)
                    JOptionPane.showMessageDialog(null, "Package(s) "
                            + removedPackageNames.toString() + " removed. "
                            + "Note that any changes to the BEAUti "
                            + "interface will\n not appear until a "
                            + "new document is created or BEAUti is "
                            + "restarted.");
            });
        box.add(uninstallButton);
        
        JButton packageRepoButton = new JButton("Package repositories");
        packageRepoButton.addActionListener(e -> {
                JPackageRepositoryDialog dlg = new JPackageRepositoryDialog(frame);
                dlg.setVisible(true);
                resetPackages();
            });
        box.add(packageRepoButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            	if (dlg != null) {
            		dlg.setVisible(false);
            	} else {
            		setVisible(false);
            	}
            });
        box.add(Box.createGlue());
        box.add(closeButton);
        box.add(Box.createGlue());

        JButton button = new JButton("?");
        button.setToolTipText(getPackageUserDir() + " " + getPackageSystemDir());
        button.addActionListener(e -> {
                JOptionPane.showMessageDialog(scrollPane, "<html>By default, packages are installed in <br><br><em>" + getPackageUserDir() +
                        "</em><br><br>and are available only to you.<br>" +
                        "<br>Packages can also be moved manually to <br><br><em>" + getPackageSystemDir() +
                        "</em><br><br>which makes them available to all users<br>"
                        + "on your system.</html>");
            });
        box.add(button);
        return box;
    }

    protected boolean doUninstall(StringBuilder removedPackageNames) {
        int[] selectedRows = dataTable.getSelectedRows();
        
        boolean toDeleteFileExists = false;
        for (int selRow : selectedRows) {
            Package selPackage = getSelectedPackage(selRow);
            if (selPackage != null) {
                try {
                    if (selPackage.isInstalled()) {
//                    if (JOptionPane.showConfirmDialog(null, "Are you sure you want to uninstall " +
//                    AddOnManager.URL2PackageName(package.url) + "?", "Uninstall Add On",
//                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        uninstallPackage(selPackage, false, null, packages, allDepCheckBox.isSelected());
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

                        File toDeleteFile = getToDeleteListFile();
                        if (toDeleteFile.exists()) {
                            toDeleteFileExists = true;
                        }

                        if (removedPackageNames.length()>0)
                            removedPackageNames.append(", ");
                        removedPackageNames.append("'")
                                .append(selPackage.packageName)
                                .append("'");
//                    }
                    } else {
                        //TODO ?
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Uninstall failed because: " + ex.getMessage());
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }	
        return toDeleteFileExists;
	}

	class DataTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		String[] columnNames = {"Name", "Status/Version", "Latest", "Dependencies", "Detail"};

        @Override
		public int getColumnCount() {
            return columnNames.length;
        }

        @Override
		public int getRowCount() {
            return packages.size();
        }

        @Override
		public Object getValueAt(int row, int col) {
            Package aPackage = packages.get(row);
            switch (col) {
                case 0:
                    return aPackage.packageName;
                case 1:
                    return aPackage.getStatus();
                case 2:
                    return aPackage.getLatestVersion();
                case 3:
                    return aPackage.getDependenciesString();
                case 4:
                    return aPackage.description;
                default:
                    throw new IllegalArgumentException("unknown column, " + col);
            }
        }

        @Override
		public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
		public String toString() {
            StringBuffer buffer = new StringBuffer();

            buffer.append(getColumnName(0));
            for (int j = 1; j < getColumnCount(); j++) {
                buffer.append("\t");
                buffer.append(getColumnName(j));
            }
            buffer.append("\n");

            for (int i = 0; i < getRowCount(); i++) {
                buffer.append(getValueAt(i, 0));
                for (int j = 1; j < getColumnCount(); j++) {
                    buffer.append("\t");
                    buffer.append(getValueAt(i, j));
                }
                buffer.append("\n");
            }

            return buffer.toString();
        }
    }

	
	
	public JDialog asDialog(JFrame frame) {
		if (frame == null) {
	        frame = (JFrame) SwingUtilities.getWindowAncestor(this);
		}
		this.frame = frame;
    	dlg = new JDialog(frame, "BEAST 2 Package Manager", true);
		dlg.getContentPane().add(scrollPane, BorderLayout.CENTER);  
		dlg.getContentPane().add(jLabel, BorderLayout.NORTH);  
		dlg.getContentPane().add(buttonBox, BorderLayout.SOUTH);  
		dlg.pack();  
        Point frameLocation = frame.getLocation();
        Dimension frameSize = frame.getSize();
        Dimension dim = getPreferredSize();
		dlg.setSize(690, 430);
        dlg.setLocation(frameLocation.x + frameSize.width / 2 - dim.width / 2, frameLocation.y + frameSize.height / 2 - dim.height / 2);

        frame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        return dlg;
	}


	JDialog dlg = null;
	@Override
	public void setCursor(Cursor cursor) {
		if (dlg != null) {
			dlg.setCursor(cursor);
		} else {
			super.setCursor(cursor);
		}
	}
}
