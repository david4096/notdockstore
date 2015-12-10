package io.dockstore.client.cli.cwl;

/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
@SuppressWarnings("all")
/** Define an enumerated type.
 */
@org.apache.avro.specific.AvroGenerated
public class EnumSchema extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"EnumSchema\",\"doc\":\"Define an enumerated type.\\n\",\"fields\":[{\"name\":\"type\",\"type\":{\"type\":\"enum\",\"name\":\"Enum_symbol\",\"symbols\":[\"enum\"]},\"doc\":\"Must be `enum`\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"}},{\"name\":\"symbols\",\"type\":[{\"type\":\"array\",\"items\":\"string\"}],\"doc\":\"Defines the set of valid symbols.\",\"jsonldPredicate\":{\"_type\":\"@id\",\"_id\":\"https://w3id.org/cwl/salad#symbols\",\"identity\":true}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
  /** Must be `enum` */
  @Deprecated public Enum_symbol type;
  /** Defines the set of valid symbols. */
  @Deprecated public java.lang.Object symbols;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>. 
   */
  public EnumSchema() {}

  /**
   * All-args constructor.
   */
  public EnumSchema(Enum_symbol type, java.lang.Object symbols) {
    this.type = type;
    this.symbols = symbols;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return type;
    case 1: return symbols;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: type = (Enum_symbol)value$; break;
    case 1: symbols = (java.lang.Object)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'type' field.
   * Must be `enum`   */
  public Enum_symbol getType() {
    return type;
  }

  /**
   * Sets the value of the 'type' field.
   * Must be `enum`   * @param value the value to set.
   */
  public void setType(Enum_symbol value) {
    this.type = value;
  }

  /**
   * Gets the value of the 'symbols' field.
   * Defines the set of valid symbols.   */
  public java.lang.Object getSymbols() {
    return symbols;
  }

  /**
   * Sets the value of the 'symbols' field.
   * Defines the set of valid symbols.   * @param value the value to set.
   */
  public void setSymbols(java.lang.Object value) {
    this.symbols = value;
  }

  /** Creates a new EnumSchema RecordBuilder */
  public static EnumSchema.Builder newBuilder() {
    return new EnumSchema.Builder();
  }
  
  /** Creates a new EnumSchema RecordBuilder by copying an existing Builder */
  public static EnumSchema.Builder newBuilder(EnumSchema.Builder other) {
    return new EnumSchema.Builder(other);
  }
  
  /** Creates a new EnumSchema RecordBuilder by copying an existing EnumSchema instance */
  public static EnumSchema.Builder newBuilder(EnumSchema other) {
    return new EnumSchema.Builder(other);
  }
  
  /**
   * RecordBuilder for EnumSchema instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<EnumSchema>
    implements org.apache.avro.data.RecordBuilder<EnumSchema> {

    private Enum_symbol type;
    private java.lang.Object symbols;

    /** Creates a new Builder */
    private Builder() {
      super(EnumSchema.SCHEMA$);
    }
    
    /** Creates a Builder by copying an existing Builder */
    private Builder(EnumSchema.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.type)) {
        this.type = data().deepCopy(fields()[0].schema(), other.type);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.symbols)) {
        this.symbols = data().deepCopy(fields()[1].schema(), other.symbols);
        fieldSetFlags()[1] = true;
      }
    }
    
    /** Creates a Builder by copying an existing EnumSchema instance */
    private Builder(EnumSchema other) {
            super(EnumSchema.SCHEMA$);
      if (isValidValue(fields()[0], other.type)) {
        this.type = data().deepCopy(fields()[0].schema(), other.type);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.symbols)) {
        this.symbols = data().deepCopy(fields()[1].schema(), other.symbols);
        fieldSetFlags()[1] = true;
      }
    }

    /** Gets the value of the 'type' field */
    public Enum_symbol getType() {
      return type;
    }
    
    /** Sets the value of the 'type' field */
    public EnumSchema.Builder setType(Enum_symbol value) {
      validate(fields()[0], value);
      this.type = value;
      fieldSetFlags()[0] = true;
      return this; 
    }
    
    /** Checks whether the 'type' field has been set */
    public boolean hasType() {
      return fieldSetFlags()[0];
    }
    
    /** Clears the value of the 'type' field */
    public EnumSchema.Builder clearType() {
      type = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /** Gets the value of the 'symbols' field */
    public java.lang.Object getSymbols() {
      return symbols;
    }
    
    /** Sets the value of the 'symbols' field */
    public EnumSchema.Builder setSymbols(java.lang.Object value) {
      validate(fields()[1], value);
      this.symbols = value;
      fieldSetFlags()[1] = true;
      return this; 
    }
    
    /** Checks whether the 'symbols' field has been set */
    public boolean hasSymbols() {
      return fieldSetFlags()[1];
    }
    
    /** Clears the value of the 'symbols' field */
    public EnumSchema.Builder clearSymbols() {
      symbols = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    public EnumSchema build() {
      try {
        EnumSchema record = new EnumSchema();
        record.type = fieldSetFlags()[0] ? this.type : (Enum_symbol) defaultValue(fields()[0]);
        record.symbols = fieldSetFlags()[1] ? this.symbols : (java.lang.Object) defaultValue(fields()[1]);
        return record;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }
}
