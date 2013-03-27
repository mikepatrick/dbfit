package dbfit.fixture;
import dbfit.util.*;
import fit.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public abstract class RowSetFixture extends ColumnFixture {
	private DataTable actualDataTable;
	private DataRow currentActualDataRow;
	
	private class CurrentDataRowTypeAdapter extends TypeAdapter {
	    public String columnName;
	    @SuppressWarnings("unchecked")
	    public CurrentDataRowTypeAdapter(String columnName, Class type) throws NoSuchMethodException {
	    	this.fixture=RowSetFixture.this;
	    	target = null;
	        method = CurrentDataRowTypeAdapter.class.getMethod("get", new Class[] {});
	        this.type = type;
	        this.columnName = columnName;
	    }
	    public void set(Object value) throws Exception {
	    	throw new UnsupportedOperationException("changing values in row sets is not supported");
	    }
	    public Object get() {
	           return currentActualDataRow.get(columnName);
	    }
	    public Object invoke() throws IllegalAccessException {
	        return get();
	    }
	    @Override
	    public Object parse(String s) throws Exception {
	    	return new ParseHelper(this.fixture,this.type).parse(s);
	    }
	}

    public static class ExpectedDataColumn {
        private String name;

        public ExpectedDataColumn(String name) {
            this.name = name;
        }

        public boolean isKey() {
            return !name.endsWith("?");
        }
    }

    // if element not 0, fixture column -> result set column index
	private String[] keyColumns;

    protected void bind(Parse heads) {
        try {
            columnBindings = new Binding[heads.size()];
            keyColumns = new String[heads.size()];
            for (int i = 0; heads != null; i++, heads = heads.more) {
                String name = heads.text();
                ExpectedDataColumn expectedDataColumn = new ExpectedDataColumn(name);
                columnBindings[i] = new SymbolAccessQueryBinding();
                DataColumn dataColumn = actualDataTable.getColumnNamed(name);
                String columnName = dataColumn.getName();
                if (expectedDataColumn.isKey())
                    keyColumns[i] = columnName;
                columnBindings[i].adapter = new CurrentDataRowTypeAdapter(
                        columnName,
                        getJavaClassForColumn(dataColumn)
                );
            }
        } catch (Exception sqle) {
            exception(heads, sqle);
        }
    }

    protected abstract DataTable getActualDataTable() throws SQLException;
	protected abstract boolean isOrdered();
	public void doRows(Parse rows)
	{
		try{
			actualDataTable = getActualDataTable();
			super.doRows(rows);
			addSurplusRows(rows.last());
		}
		catch (SQLException sqle){
			sqle.printStackTrace();
			exception(rows,sqle);
		}
	}

    public void doRow(Parse expectedDataRow) {
        try {
            if (isOrdered())
                currentActualDataRow = actualDataTable.findFirstUnprocessedRow();
            else
                currentActualDataRow = findMatchingRow(expectedDataRow);
            super.doRow(expectedDataRow);
            currentActualDataRow.markProcessed();
        } catch (NoMatchingRowFoundException e) {
            expectedDataRow.parts.addToBody(Fixture.gray(" missing"));
            wrong(expectedDataRow);
        }
    }

	public DataRow findMatchingRow(Parse row) throws NoMatchingRowFoundException{
        Map<String, Object> keyMap = getMapFrom(row);
		return actualDataTable.findMatching(keyMap);
	}

    private Map<String, Object> getMapFrom(Parse row) {
        Parse columns=row.parts;
        Map<String,Object> keyMap=new HashMap<String,Object>();
        for (int i=0; i<keyColumns.length; i++, columns=columns.more){
            if (keyColumns[i]!=null){
                try  {
                    Object value=columnBindings[i].adapter.parse(columns.text());
                    keyMap.put(keyColumns[i], value);
                }
                catch (Exception e){
                    exception(columns,e);
                }
            }
        }
        return keyMap;
    }

    private void addSurplusRows(Parse rows){
		Parse lastRow=rows;
		for (DataRow dr: actualDataTable.getUnprocessedRows()){
			Parse newRow=new Parse("tr",null,null,null);
			lastRow.more=newRow;
			lastRow=newRow;
			try{
				currentActualDataRow =dr; // for getting
				Parse firstCell=new Parse("td",
						String.valueOf(columnBindings[0].adapter.invoke()),null,null);
				newRow.parts=firstCell;
				firstCell.addToBody(Fixture.gray(" surplus"));
				wrong(firstCell);				
				for (int i=1; i<columnBindings.length; i++){
					Parse nextCell=new Parse("td",
							String.valueOf(columnBindings[i].adapter.invoke()),null,null);
					firstCell.more=nextCell;
					firstCell=nextCell;
				}
			}
			catch (Exception e){
				exception(newRow, e);
			}
		}
	}

    @SuppressWarnings("unchecked")
    protected Class getJavaClassForColumn(DataColumn col) throws ClassNotFoundException, SQLException {
        return col.getJavaClassForColumn();
    }
}
