var indexSpec = {
    "user" : [
        {index: {email:1}, option: {name:"email", unique:true}}
    ]
};

["user"].forEach(function(colName){
    var col = db[colName];
    if(col.stats().ok != 1) {
        print("Create collection - " + colName);
        db.createCollection(colName);
        col = db[colName];
    }

    var indexInfo = db[colName].getIndexSpecs();
    indexSpec[colName].forEach(function(spec){
        var indexName = spec.option.name;
        var indexExists = false;
        indexInfo.forEach(function(index){
            if(index.name == indexName){
                indexExists = true;
                return;
            }
        });
        if(!indexExists) {
            print("Create index:" + indexName + " for collection:" + colName);
            col.ensureIndex(spec.index, spec.option);
        }
    });
});
