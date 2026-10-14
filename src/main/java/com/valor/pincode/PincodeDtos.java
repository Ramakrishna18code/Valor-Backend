package com.valor.pincode;
import java.time.*; import java.util.*;
final class PincodeDtos { private PincodeDtos() {} record Location(String pincode,String state,String district,String postOffice,String locality,String sourceName,String sourceVersion,LocalDate sourceUpdatedAt) {} record Lookup(String pincode,List<Location> locations) {} }
